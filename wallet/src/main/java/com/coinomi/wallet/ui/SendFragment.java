package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.AmountEditView;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * Fragment that prepares a transaction
 *
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public class SendFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SendFragment.class);

    // the fragment initialization parameters
    private static final String COIN_TYPE = "coin_type";
    private static final int REQUEST_CODE_SCAN = 0;
    private static final int SIGN_TRANSACTION = 1;

    // Loader IDs
    private static final int ID_RATE_LOADER = 0;

    private CoinType type;
    @Nullable private Coin lastBalance; // TODO setup wallet watcher for the latest balance
    private Handler handler = new Handler();
    private EditText sendToAddressView;
    private TextView addressError;
    private CurrencyCalculatorLink amountCalculatorLink;
    private TextView amountError;
    private TextView amountWarning;
    private ImageButton scanQrCodeButton;
    private Button sendConfirmButton;

    private State state = State.INPUT;
    private Address address;
    private Coin sendAmount;
    @Nullable private WalletActivity activity;
    @Nullable private WalletPocket pocket;
    private Configuration config;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private LoaderManager loaderManager;


    private enum State {
        INPUT, PREPARATION, SENDING, SENT, FAILED
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param type the type of the coin
     * @return A new instance of fragment WalletSendCoins.
     */
    public static SendFragment newInstance(CoinType type) {
        SendFragment fragment = new SendFragment();
        Bundle args = new Bundle();
        args.putSerializable(COIN_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    public SendFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = (CoinType) checkNotNull(getArguments().getSerializable(COIN_TYPE));
        }
        if (activity != null) {
            pocket = activity.getWalletApplication().getWalletPocket(type);
        }
        updateBalance();
        setHasOptionsMenu(true);
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
    }

    private void updateBalance() {
        if (pocket != null) {
            lastBalance = pocket.getBalance(false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_send, container, false);

        sendToAddressView = (EditText) view.findViewById(R.id.send_to_address);
        sendToAddressView.setOnFocusChangeListener(receivingAddressListener);
        sendToAddressView.addTextChangedListener(receivingAddressListener);


        AmountEditView sendCoinAmountView = (AmountEditView) view.findViewById(R.id.send_coin_amount);
        sendCoinAmountView.setCoinType(type);
        sendCoinAmountView.setFormat(type.getMonetaryFormat());

        AmountEditView sendLocalAmountView = (AmountEditView) view.findViewById(R.id.send_local_amount);
        sendLocalAmountView.setFormat(Constants.LOCAL_CURRENCY_FORMAT);

        amountCalculatorLink = new CurrencyCalculatorLink(sendCoinAmountView, sendLocalAmountView);
        amountCalculatorLink.setExchangeDirection(config.getLastExchangeDirection());

        addressError = (TextView) view.findViewById(R.id.address_error_message);
        addressError.setVisibility(View.GONE);
        amountError = (TextView) view.findViewById(R.id.amount_error_message);
        amountError.setVisibility(View.GONE);
        amountWarning = (TextView) view.findViewById(R.id.amount_warning_message);
        amountWarning.setVisibility(View.GONE);

        scanQrCodeButton = (ImageButton) view.findViewById(R.id.scan_qr_code);
        scanQrCodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleScan();
            }
        });

        sendConfirmButton = (Button) view.findViewById(R.id.send_confirm);
        sendConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAddress();
                validateAmount();
                if (everythingValid())
                    handleSendConfirm();
                else
                    requestFocusFirst();
            }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        config.setLastExchangeDirection(amountCalculatorLink.getExchangeDirection());
    }

    @Override
    public void onResume() {
        super.onResume();

        amountCalculatorLink.setListener(amountsListener);

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

        if (pocket != null) pocket.addEventListener(transactionChangeListener, Threading.SAME_THREAD);
        updateBalance();

        updateView();
    }

    @Override
    public void onPause() {
        if (pocket != null) pocket.removeEventListener(transactionChangeListener);
        transactionChangeListener.removeCallbacks();

        loaderManager.destroyLoader(ID_RATE_LOADER);

        amountCalculatorLink.setListener(null);

        super.onPause();
    }

    private void handleScan() {
        startActivityForResult(new Intent(getActivity(), ScanActivity.class), REQUEST_CODE_SCAN);
    }

    private void handleSendConfirm() {
        if (!everythingValid()) { // Sanity check
            log.error("Unexpected validity failure.");
            validateAmount();
            validateAddress();
            return;
        }
        state = State.PREPARATION;
        updateView();
        if (activity != null && activity.getWalletApplication().getWallet() != null) {
            onMakeTransaction(address, sendAmount);
        }
        reset();
    }

    public void onMakeTransaction(Address toAddress, Coin amount) {
        Intent intent = new Intent(getActivity(), SignTransactionActivity.class);
        try {
            if (pocket == null) {
                throw new NoSuchPocketException("No pocket found for " + type.getName());
            }
            intent.putExtra(Constants.ARG_COIN_ID, type.getId());
            intent.putExtra(Constants.ARG_SEND_TO_ADDRESS, toAddress.toString());
            intent.putExtra(Constants.ARG_SEND_AMOUNT, amount.getValue());
            startActivityForResult(intent, SIGN_TRANSACTION);
        } catch (NoSuchPocketException e) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
        }
    }

    private void reset() {
        sendToAddressView.setText(null);
        amountCalculatorLink.setCoinAmount(null);
        address = null;
        sendAmount = null;
        state = State.INPUT;
        addressError.setVisibility(View.GONE);
        amountError.setVisibility(View.GONE);
        amountWarning.setVisibility(View.GONE);
        updateView();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                try {
                    final CoinURI coinUri = new CoinURI(type, input);

                    Address address = coinUri.getAddress();
                    Coin amount = coinUri.getAmount();
                    String label = coinUri.getLabel();

                    updateStateFrom(address, amount, label);
                } catch (final CoinURIParseException x) {
                    String error = getResources().getString(R.string.uri_error, x.getMessage());
                    Toast.makeText(getActivity(), error, Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == SIGN_TRANSACTION) {
            if (resultCode == Activity.RESULT_OK) {
                Exception error = (Exception) intent.getSerializableExtra(Constants.ARG_ERROR);

                if (error == null) {
                    Toast.makeText(getActivity(), R.string.sending_msg, Toast.LENGTH_SHORT).show();
                } else {
                    if (error instanceof InsufficientMoneyException) {
                        Toast.makeText(getActivity(), R.string.amount_error_not_enough_money, Toast.LENGTH_LONG).show();
                    } else if (error instanceof NoSuchPocketException) {
                        Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
                    } else if (error instanceof KeyCrypterException) {
                        Toast.makeText(getActivity(), R.string.password_failed, Toast.LENGTH_LONG).show();
                    } else if (error instanceof IOException) {
                        Toast.makeText(getActivity(), R.string.send_coins_error_network, Toast.LENGTH_LONG).show();
                    } else if (error instanceof Wallet.DustySendRequested) {
                        Toast.makeText(getActivity(), R.string.send_coins_error_dust, Toast.LENGTH_LONG).show();
                    } else {
                        log.error("An unknown error occurred while sending coins", error);
                        String errorMessage = getString(R.string.send_coins_error, error.getMessage());
                        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    void updateStateFrom(final Address address, final @Nullable Coin amount,
                         final @Nullable String label) throws CoinURIParseException {
        log.info("got {}", address);
        if (address == null) {
            throw new CoinURIParseException("missing address");
        }

        // delay these actions until fragment is resumed
        handler.post(new Runnable() {
            @Override
            public void run() {
                sendToAddressView.setText(address.toString());
                if (amount != null) amountCalculatorLink.setCoinAmount(amount);
                validateEverything();
                requestFocusFirst();
            }
        });
    }

    private void updateView() {
        sendConfirmButton.setEnabled(everythingValid());

        // enable actions
        if (scanQrCodeButton != null) {
            scanQrCodeButton.setEnabled(state == State.INPUT);
        }
    }

    private boolean isOutputsValid() {
        return address != null;
    }

    private boolean isAmountValid() {
        return isAmountValid(sendAmount);
    }

    private boolean isAmountValid(Coin amount) {
        boolean isValid = amount != null
                && amount.isPositive()
                && amount.compareTo(type.getMinNonDust()) >= 0;
        if (isValid && lastBalance != null) {
            // Check if we have the amount
            isValid = amount.compareTo(lastBalance) <= 0;
        }
        return isValid;
    }

    private boolean everythingValid() {
        return state == State.INPUT && isOutputsValid() && isAmountValid();
    }

    private void requestFocusFirst() {
        if (!isOutputsValid()) {
            sendToAddressView.requestFocus();
        } else if (!isAmountValid()) {
            amountCalculatorLink.requestFocus();
            // FIXME causes problems in older Androids
//            Keyboard.focusAndShowKeyboard(sendAmountView, getActivity());
        } else if (everythingValid()) {
            sendConfirmButton.requestFocus();
        } else {
            log.warn("unclear focus");
        }
    }

    private void validateEverything() {
        validateAddress();
        validateAmount();
    }

    private void validateAmount() {
        validateAmount(false);
    }

    private void validateAmount(boolean isTyping) {
        Coin amountParsed = amountCalculatorLink.getAmount();

        if (isAmountValid(amountParsed)) {
            sendAmount = amountParsed;
            amountError.setVisibility(View.GONE);
            // Show warning that fees apply when entered the full amount inside the pocket
            if (sendAmount != null && lastBalance != null && sendAmount.compareTo(lastBalance) == 0) {
                amountWarning.setText(R.string.amount_warn_fees_apply);
                amountWarning.setVisibility(View.VISIBLE);
            } else {
                amountWarning.setVisibility(View.GONE);
            }
        } else {
            amountWarning.setVisibility(View.GONE);
            // ignore printing errors for null and zero amounts
            if (shouldShowErrors(isTyping, amountParsed)) {
                sendAmount = null;
                if (amountParsed == null) {
                    amountError.setText(R.string.amount_error);
                } else if (amountParsed.isNegative()) {
                    amountError.setText(R.string.amount_error_negative);
                } else if (amountParsed.compareTo(type.getMinNonDust()) < 0) {
                    String minAmount = GenericUtils.formatCoinValue(type, type.getMinNonDust());
                    String message = getResources().getString(R.string.amount_error_too_small,
                            minAmount, type.getSymbol());
                    amountError.setText(message);
                } else if (lastBalance != null && amountParsed.compareTo(lastBalance) > 0) {
                    amountError.setText(R.string.amount_error_not_enough_money);
                } else { // Should not happen, but show a generic error
                    amountError.setText(R.string.amount_error);
                }
                amountError.setVisibility(View.VISIBLE);
            } else {
                amountError.setVisibility(View.GONE);
            }
        }
        updateView();
    }

    /**
     * Show errors if the user is not typing and the input is not empty and the amount is zero.
     * Exception is when the amount is lower than the available balance
     */
    private boolean shouldShowErrors(boolean isTyping, Coin amountParsed) {
        if (amountParsed != null && lastBalance != null && amountParsed.compareTo(lastBalance) >= 0) return true;

        if (isTyping) return false;
        if (amountCalculatorLink.isEmpty()) return false;
        if (amountParsed != null && amountParsed.isZero()) return false;

        return true;
    }

    private void validateAddress() {
        validateAddress(false);
    }

    private void validateAddress(boolean isTyping) {
        String addressStr = sendToAddressView.getText().toString().trim();

        // If not typing, try to fix address if needed
        if (!isTyping) {
            addressStr = GenericUtils.fixAddress(addressStr);
            // Remove listener before changing input, then add it again. Hack to avoid stack overflow
            sendToAddressView.removeTextChangedListener(receivingAddressListener);
            sendToAddressView.setText(addressStr);
            sendToAddressView.addTextChangedListener(receivingAddressListener);
        }

        try {
            if (!addressStr.isEmpty()) {
                address = new Address(type, addressStr);
            } else {
                // empty field should not raise error message
                address = null;
            }
            addressError.setVisibility(View.GONE);
        } catch (final AddressFormatException x) {
            // could not decode address at all
            if (!isTyping) {
                address = null;
                addressError.setText(R.string.address_error);
                addressError.setVisibility(View.VISIBLE);
            }
        }

        updateView();
    }

    private void setAmountForEmptyWallet() {
        updateBalance();
        if (state != State.INPUT || pocket == null || lastBalance == null) return;

        if (lastBalance.isZero()) {
            Toast.makeText(getActivity(), R.string.amount_error_not_enough_money,
                    Toast.LENGTH_LONG).show();
        } else {
            amountCalculatorLink.setCoinAmount(lastBalance);
            validateAmount();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            inflater.inflate(R.menu.send, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_empty_wallet:
                setAmountForEmptyWallet();
                return true;
            default:
                // Not one of ours. Perform default menu processing
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            this.activity = (WalletActivity) activity;
            this.config = ((WalletActivity) activity).getWalletApplication().getConfiguration();
            this.loaderManager = getLoaderManager();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + WalletActivity.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }

    private abstract class EditViewListener implements View.OnFocusChangeListener, TextWatcher {
        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        }
    }


    EditViewListener receivingAddressListener = new EditViewListener() {
        @Override
        public void onFocusChange(final View v, final boolean hasFocus) {
            if (!hasFocus) {
                validateAddress();
            }
        }

        @Override
        public void afterTextChanged(final Editable s) {
            validateAddress(true);
        }
    };

    private final AmountEditView.Listener amountsListener = new AmountEditView.Listener() {
        @Override
        public void changed() {
            validateAmount(true);
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (!hasFocus) {
                validateAmount();
            }
        }
    };

    private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            String localSymbol = config.getExchangeCurrencyCode();
            String coinSymbol = type.getSymbol();
            return new ExchangeRateLoader(getActivity(), config, localSymbol, coinSymbol);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                data.moveToFirst();
                final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);

                if (state == State.INPUT) {
                    amountCalculatorLink.setExchangeRate(exchangeRate.rate);
                }
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };

    private final ThrottlingWalletChangeListener transactionChangeListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            updateBalance();
            validateAmount();
        }
    };

}
