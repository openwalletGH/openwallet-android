package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletPocketHD;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.adaptors.AvailableAccountsAdaptor;
import com.coinomi.wallet.ui.widget.AmountEditView;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
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
public class TradeSelectFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(TradeSelectFragment.class);

    // the fragment initialization parameters
    private static final int REQUEST_CODE_SCAN = 0;
    private static final int SIGN_TRANSACTION = 1;

    private static final int UPDATE_WALLET_CHANGE = 1;

    // Loader IDs
    private static final int ID_RECEIVING_ADDRESS_LOADER = 0;

    private CoinType fromCoinType;
    private CoinType toCoinType;
    @Nullable private Coin lastBalance; // TODO setup wallet watcher for the latest balance
//    private AutoCompleteTextView sendToAddressView;
//    private TextView addressError;
    private CurrencyCalculatorLink amountCalculatorLink;
    private TextView receiveCoinWarning;
    private TextView amountError;
    private TextView amountWarning;
//    private ImageButton scanQrCodeButton;
    private Button nextButton;

//    private Address address;
    private Coin tradeAmount;
    private Coin receiveAmount;
    @Nullable private BaseWalletActivity activity;
    @Nullable private WalletPocketHD pocket;
    @Nullable private Wallet wallet;
    private Configuration config;
//    private ReceivingAddressViewAdapter sendToAddressViewAdapter;

    Handler handler = new MyHandler(this);
    private static class MyHandler extends WeakHandler<TradeSelectFragment> {
        public MyHandler(TradeSelectFragment referencingObject) { super(referencingObject); }

        @Override
        protected void weakHandleMessage(TradeSelectFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_WALLET_CHANGE:
                    ref.onWalletUpdate();
            }
        }
    }

    public TradeSelectFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

//        loaderManager.initLoader(ID_RECEIVING_ADDRESS_LOADER, null, receivingAddressLoaderCallbacks);

        wallet = activity.getWallet();
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
        View view = inflater.inflate(R.layout.fragment_trade_select, container, false);

//        sendToAddressView = (AutoCompleteTextView) view.findViewById(R.id.custom_receive_address);
//        sendToAddressViewAdapter = new ReceivingAddressViewAdapter(activity);
//        sendToAddressView.setAdapter(sendToAddressViewAdapter);
//        sendToAddressView.setOnFocusChangeListener(receivingAddressListener);
//        sendToAddressView.addTextChangedListener(receivingAddressListener);

        // TODO make dynamic
        fromCoinType = BitcoinMain.get();
        toCoinType = LitecoinMain.get();
        pocket = (WalletPocketHD) activity.getWalletApplication().getAccounts(fromCoinType).get(0);


        Spinner fromCoinSpinner = (Spinner) view.findViewById(R.id.from_coin);
        fromCoinSpinner.setAdapter(new AvailableAccountsAdaptor(activity, wallet.getAllAccounts()));
        Spinner toCoinSpinner = (Spinner) view.findViewById(R.id.to_coin);
        toCoinSpinner.setAdapter(new AvailableAccountsAdaptor(activity, Constants.SUPPORTED_COINS));

        AmountEditView tradeCoinAmountView = (AmountEditView) view.findViewById(R.id.trade_coin_amount);
        tradeCoinAmountView.setCoinType(fromCoinType);
        tradeCoinAmountView.setFormat(fromCoinType.getMonetaryFormat());

        AmountEditView receiveCoinAmountView = (AmountEditView) view.findViewById(R.id.receive_coin_amount);
        receiveCoinAmountView.setCoinType(toCoinType);
        // TODO use CoinType instead local currency
//        receiveCoinAmountView.setFormat(toCoinType.getMonetaryFormat());
        receiveCoinAmountView.setFormat(Constants.LOCAL_CURRENCY_FORMAT);

        amountCalculatorLink = new CurrencyCalculatorLink(tradeCoinAmountView, receiveCoinAmountView);
        // TODO get rate from shapeshift and don't use Fiat
        amountCalculatorLink.setExchangeRate(new ExchangeRate(Fiat.parseFiat(toCoinType.getSymbol(), "142.9856")));
//        amountCalculatorLink.setExchangeDirection(config.getLastExchangeDirection());

        receiveCoinWarning = (TextView) view.findViewById(R.id.warn_no_account_found);
        receiveCoinWarning.setVisibility(View.GONE);
//        addressError = (TextView) view.findViewById(R.id.address_error_message);
//        addressError.setVisibility(View.GONE);
        amountError = (TextView) view.findViewById(R.id.amount_error_message);
        amountError.setVisibility(View.GONE);
        amountWarning = (TextView) view.findViewById(R.id.amount_warning_message);
        amountWarning.setVisibility(View.GONE);

//        scanQrCodeButton = (ImageButton) view.findViewById(R.id.scan_qr_code);
//        scanQrCodeButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                handleScan();
//            }
//        });

        nextButton = (Button) view.findViewById(R.id.button_next);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("TODO next");
//                validateAddress();
//                validateAmount();
//                if (everythingValid())
//                    handleSendConfirm();
//                else
//                    requestFocusFirst();
            }
        });

        return view;
    }

    @Override
    public void onDestroy() {
//        loaderManager.destroyLoader(ID_RECEIVING_ADDRESS_LOADER);

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        amountCalculatorLink.setListener(amountsListener);

        if (pocket != null)
            pocket.addEventListener(transactionChangeListener, Threading.SAME_THREAD);
        updateBalance();

        updateView();
    }

    @Override
    public void onPause() {
        if (pocket != null) pocket.removeEventListener(transactionChangeListener);
        transactionChangeListener.removeCallbacks();

        amountCalculatorLink.setListener(null);

        super.onPause();
    }

    private void handleScan() {
        startActivityForResult(new Intent(getActivity(), ScanActivity.class), REQUEST_CODE_SCAN);
    }

    private void handleSendConfirm() {
        // TODO
//        if (!everythingValid()) { // Sanity check
//            log.error("Unexpected validity failure.");
//            validateAmount();
//            validateAddress();
//            return;
//        }
////        state = State.PREPARATION;
//        updateView();
//        if (activity != null && activity.getWalletApplication().getWallet() != null) {
//            onMakeTransaction(address, sendAmount);
//        }
//        reset();
    }

    public void onMakeTransaction(Address toAddress, Coin amount) {
        // TODO
//        Intent intent = new Intent(getActivity(), SignTransactionActivity.class);
//        try {
//            if (pocket == null) {
//                throw new NoSuchPocketException("No pocket found for " + type.getName());
//            }
//            intent.putExtra(Constants.ARG_ACCOUNT_ID, pocket.getId());
//            intent.putExtra(Constants.ARG_SEND_TO_ADDRESS, toAddress.toString());
//            intent.putExtra(Constants.ARG_SEND_AMOUNT, amount.getValue());
//            startActivityForResult(intent, SIGN_TRANSACTION);
//        } catch (NoSuchPocketException e) {
//            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
//        }
    }

    private void reset() {
//        sendToAddressView.setText(null);
        amountCalculatorLink.setCoinAmount(null);
        tradeAmount = null;
        receiveAmount = null;
//        state = State.INPUT;
//        addressError.setVisibility(View.GONE);
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
                    final CoinURI coinUri = new CoinURI(toCoinType, input);

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
                    } else if (error instanceof org.bitcoinj.core.Wallet.DustySendRequested) {
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
        // TODO
//        log.info("got {}", address);
//        if (address == null) {
//            throw new CoinURIParseException("missing address");
//        }
//
//        // delay these actions until fragment is resumed
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                sendToAddressView.setText(address.toString());
//                if (amount != null) amountCalculatorLink.setCoinAmount(amount);
//                validateEverything();
//                requestFocusFirst();
//            }
//        });
    }

    private void updateView() {
        nextButton.setEnabled(everythingValid());

        // enable actions
        // TODO
//        if (scanQrCodeButton != null) {
//            scanQrCodeButton.setEnabled(state == State.INPUT);
//        }
    }

    private boolean isOutputsValid() {
        // TODO
//        return address != null;
        return true;
    }

    private boolean isAmountValid() {
        return isAmountValid(fromCoinType, tradeAmount, true) &&
                isAmountValid(toCoinType, receiveAmount, false);
    }

    private boolean isAmountValid(CoinType type, Coin amount, boolean isFromMe) {
        boolean isValid = amount != null
                && amount.isPositive()
                && amount.compareTo(type.getMinNonDust()) >= 0;
        if (isFromMe && isValid && lastBalance != null) {
            // Check if we have the amount
            isValid = amount.compareTo(lastBalance) <= 0;
        }
        return isValid;
    }

    private boolean everythingValid() {
//        return state == State.INPUT && isOutputsValid() && isAmountValid();
        return isOutputsValid() && isAmountValid();
    }

    private void requestFocusFirst() {
        if (!isOutputsValid()) {
            // TODO
//            sendToAddressView.requestFocus();
        } else if (!isAmountValid()) {
            amountCalculatorLink.requestFocus();
        } else if (everythingValid()) {
            nextButton.requestFocus();
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
        // TODO
//        Coin amountParsed = amountCalculatorLink.getAmount();
//
//        if (isAmountValid(amountParsed)) {
//            sendAmount = amountParsed;
//            amountError.setVisibility(View.GONE);
//            // Show warning that fees apply when entered the full amount inside the pocket
//            if (sendAmount != null && lastBalance != null && sendAmount.compareTo(lastBalance) == 0) {
//                amountWarning.setText(R.string.amount_warn_fees_apply);
//                amountWarning.setVisibility(View.VISIBLE);
//            } else {
//                amountWarning.setVisibility(View.GONE);
//            }
//        } else {
//            amountWarning.setVisibility(View.GONE);
//            // ignore printing errors for null and zero amounts
//            if (shouldShowErrors(isTyping, amountParsed)) {
//                sendAmount = null;
//                if (amountParsed == null) {
//                    amountError.setText(R.string.amount_error);
//                } else if (amountParsed.isNegative()) {
//                    amountError.setText(R.string.amount_error_negative);
//                } else if (amountParsed.compareTo(type.getMinNonDust()) < 0) {
//                    String minAmount = GenericUtils.formatCoinValue(type, type.getMinNonDust());
//                    String message = getResources().getString(R.string.amount_error_too_small,
//                            minAmount, type.getSymbol());
//                    amountError.setText(message);
//                } else if (lastBalance != null && amountParsed.compareTo(lastBalance) > 0) {
//                    amountError.setText(R.string.amount_error_not_enough_money);
//                } else { // Should not happen, but show a generic error
//                    amountError.setText(R.string.amount_error);
//                }
//                amountError.setVisibility(View.VISIBLE);
//            } else {
//                amountError.setVisibility(View.GONE);
//            }
//        }
//        updateView();
    }

    /**
     * Show errors if the user is not typing and the input is not empty and the amount is zero.
     * Exception is when the amount is lower than the available balance
     */
    private boolean shouldShowErrors(boolean isTyping, Coin amountParsed) {
        if (amountParsed != null && lastBalance != null && amountParsed.compareTo(lastBalance) >= 0)
            return true;

        if (isTyping) return false;
        if (amountCalculatorLink.isEmpty()) return false;
        if (amountParsed != null && amountParsed.isZero()) return false;

        return true;
    }

    private void validateAddress() {
        validateAddress(false);
    }

    private void validateAddress(boolean isTyping) {
        // TODO
//        String addressStr = sendToAddressView.getText().toString().trim();
//
//        // If not typing, try to fix address if needed
//        if (!isTyping) {
//            addressStr = GenericUtils.fixAddress(addressStr);
//            // Remove listener before changing input, then add it again. Hack to avoid stack overflow
//            sendToAddressView.removeTextChangedListener(receivingAddressListener);
//            sendToAddressView.setText(addressStr);
//            sendToAddressView.addTextChangedListener(receivingAddressListener);
//        }
//
//        try {
//            if (!addressStr.isEmpty()) {
//                address = new Address(type, addressStr);
//            } else {
//                // empty field should not raise error message
//                address = null;
//            }
//            addressError.setVisibility(View.GONE);
//        } catch (final AddressFormatException x) {
//            // could not decode address at all
//            if (!isTyping) {
//                address = null;
//                addressError.setText(R.string.address_error);
//                addressError.setVisibility(View.VISIBLE);
//            }
//        }
//
//        updateView();
    }

    private void setAmountForEmptyWallet() {
        updateBalance();
//        if (state != State.INPUT || pocket == null || lastBalance == null) return;
        if (pocket == null || lastBalance == null) return;

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
        inflater.inflate(R.menu.trade, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_swap_coins:
                setAmountForEmptyWallet();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            this.activity = (BaseWalletActivity) activity;
            this.config = this.activity.getConfiguration();
//            this.loaderManager = getLoaderManager();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + BaseWalletActivity.class);
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

    private void onWalletUpdate() {
        updateBalance();
        validateAmount();
    }

    private final ThrottlingWalletChangeListener transactionChangeListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            handler.sendMessage(handler.obtainMessage(UPDATE_WALLET_CHANGE));
        }
    };

//    private final LoaderCallbacks<Cursor> receivingAddressLoaderCallbacks = new LoaderCallbacks<Cursor>() {
//        @Override
//        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
//            final String constraint = args != null ? args.getString("constraint") : null;
//            Uri uri = AddressBookProvider.contentUri(activity.getPackageName(), type);
//            return new CursorLoader(activity, uri, null, AddressBookProvider.SELECTION_QUERY,
//                    new String[]{constraint != null ? constraint : ""}, null);
//        }
//
//        @Override
//        public void onLoadFinished(final Loader<Cursor> cursor, final Cursor data) {
//            sendToAddressViewAdapter.swapCursor(data);
//        }
//
//        @Override
//        public void onLoaderReset(final Loader<Cursor> cursor) {
//            sendToAddressViewAdapter.swapCursor(null);
//        }
//    };
//
//    private final class ReceivingAddressViewAdapter extends CursorAdapter implements FilterQueryProvider {
//        public ReceivingAddressViewAdapter(final Context context) {
//            super(context, null, false);
//            setFilterQueryProvider(this);
//        }
//
//        @Override
//        public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
//            final LayoutInflater inflater = LayoutInflater.from(context);
//            return inflater.inflate(R.layout.address_book_row, parent, false);
//        }
//
//        @Override
//        public void bindView(final View view, final Context context, final Cursor cursor) {
//            final String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
//            final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
//
//            final ViewGroup viewGroup = (ViewGroup) view;
//            final TextView labelView = (TextView) viewGroup.findViewById(R.id.address_book_row_label);
//            labelView.setText(label);
//            final TextView addressView = (TextView) viewGroup.findViewById(R.id.address_book_row_address);
//            addressView.setText(GenericUtils.addressSplitToGroupsMultiline(address));
//        }
//
//        @Override
//        public CharSequence convertToString(final Cursor cursor) {
//            return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
//        }
//
//        @Override
//        public Cursor runQuery(final CharSequence constraint) {
//            final Bundle args = new Bundle();
//            if (constraint != null)
//                args.putString("constraint", constraint.toString());
//            loaderManager.restartLoader(ID_RECEIVING_ADDRESS_LOADER, args, receivingAddressLoaderCallbacks);
//            return getCursor();
//        }
//    }
}
