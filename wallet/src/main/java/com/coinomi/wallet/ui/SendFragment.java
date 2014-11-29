package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.QrCodeButton;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 *  Fragment that prepares a transaction
 */
public class SendFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SendFragment.class);

    // the fragment initialization parameters
    private static final String COIN_TYPE = "coin_type";
    private static final int REQUEST_CODE_SCAN = 0;
    private static final int SIGN_TRANSACTION = 1;

    private CoinType coinType;
    private Handler handler = new Handler();
    private EditText sendToAddressView;
    private TextView addressError;
    private EditText sendAmountView;
    private TextView amountError;
    private TextView amountWarning;
    private QrCodeButton scanQrCodeButton;
    private Button sendConfirmButton;

    private State state = State.INPUT;
    private Address address;
    private Coin sendAmount;
    @Nullable private WalletActivity mListener;
    @Nullable private WalletPocket pocket;
    private NavigationDrawerFragment mNavigationDrawerFragment;


    private enum State {
        INPUT, PREPARATION, SENDING, SENT, FAILED
    }

    private final SendAmountListener sendAmountListener = new SendAmountListener();
    private final ReceivingAddressListener receivingAddressListener = new ReceivingAddressListener();

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
            coinType = (CoinType) checkNotNull(getArguments().getSerializable(COIN_TYPE));
        }
        if (mListener != null) {
            pocket = mListener.getWalletApplication().getWalletPocket(coinType);
        }
        setHasOptionsMenu(true);
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_send, container, false);

        sendToAddressView = (EditText) view.findViewById(R.id.send_to_address);
        sendToAddressView.setOnFocusChangeListener(receivingAddressListener);
        sendToAddressView.addTextChangedListener(receivingAddressListener);

        sendAmountView = (EditText) view.findViewById(R.id.send_amount);
        sendAmountView.setOnFocusChangeListener(sendAmountListener);
        sendAmountView.addTextChangedListener(sendAmountListener);

        addressError = (TextView) view.findViewById(R.id.address_error_message);
        addressError.setVisibility(View.GONE);
        amountError = (TextView) view.findViewById(R.id.amount_error_message);
        amountError.setVisibility(View.GONE);
        amountWarning = (TextView) view.findViewById(R.id.amount_warning_message);
        amountWarning.setVisibility(View.GONE);

        scanQrCodeButton = (QrCodeButton) view.findViewById(R.id.scan_qr_code);
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

        ((TextView)view.findViewById(R.id.symbol)).setText(coinType.getSymbol());

        return view;
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
        if (mListener != null && mListener.getWalletApplication().getWallet() != null) {
            onMakeTransaction(address, sendAmount);
        }
        reset();
    }

    public void onMakeTransaction(Address toAddress, Coin amount) {
        Intent intent = new Intent(getActivity(), SignTransactionActivity.class);
        try {
            if (pocket == null) {
                throw new NoSuchPocketException("No pocket found for " + coinType.getName());
            }
            intent.putExtra(Constants.ARG_COIN_ID, coinType.getId());
            intent.putExtra(Constants.ARG_SEND_TO_ADDRESS, toAddress.toString());
            intent.putExtra(Constants.ARG_SEND_AMOUNT, amount.getValue());
            startActivityForResult(intent, SIGN_TRANSACTION);
        } catch (NoSuchPocketException e) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
        }
    }

    private void reset() {
        sendToAddressView.setText(null);
        sendAmountView.setText(null);
        address = null;
        sendAmount = null;
        state = State.INPUT;
        updateView();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

                try {
                    final CoinURI coinUri = new CoinURI(coinType, input);

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
                    // TODO check for transaction broadcast in wallet pocket
                    Toast.makeText(getActivity(), R.string.sent_msg, Toast.LENGTH_LONG).show();
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
        handler.post(new Runnable()
        {
            @Override
            public void run() {
                SendFragment.this.address = address;
                sendToAddressView.setText(address.toString());
                if (isAmountValid(amount)) {
                    sendAmountView.setText(GenericUtils.formatValue(coinType, amount));
                    sendAmount = amount;
                }
                requestFocusFirst();
                updateView();
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
                && amount.compareTo(coinType.getMinNonDust()) >= 0;
        if (isValid && pocket != null) {
            // Check if we have the amount
            // FIXME optimize, pocket.getBalance() is a bit heavy but always up-to-date.
            isValid = amount.compareTo(pocket.getBalance(false)) <= 0;
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
            sendAmountView.requestFocus();
            // FIXME causes problems in older Androids
//            Keyboard.focusAndShowKeyboard(sendAmountView, getActivity());
        } else if (everythingValid()) {
            sendConfirmButton.requestFocus();
        } else {
            log.warn("unclear focus");
        }
    }

    private void validateAmount() {
        validateAmount(false);
    }

    private void validateAmount(boolean isTyping) {
        final String amountStr = sendAmountView.getText().toString().trim();
        Coin amountParsed = null;
        try {
            if (!amountStr.isEmpty()) {
                amountParsed = GenericUtils.parseCoin(coinType, amountStr);
                if (isAmountValid(amountParsed)) {
                    sendAmount = amountParsed;
                } else {
                    throw new IllegalArgumentException("Amount " + amountStr + " is invalid");
                }
            } else {
                sendAmount = null;
            }
            amountError.setVisibility(View.GONE);

            // Show warning that fees apply when entered the full amount inside the pocket
            // FIXME optimize, pocket.getBalance() is a bit heavy but always up-to-date.
            if (pocket != null && sendAmount != null && sendAmount.compareTo(pocket.getBalance()) == 0) {
                amountWarning.setText(R.string.amount_warn_fees_apply);
                amountWarning.setVisibility(View.VISIBLE);
            } else {
                amountWarning.setVisibility(View.GONE);
            }
        } catch (IllegalArgumentException e) {
            amountWarning.setVisibility(View.GONE);
            if (!isTyping) {
                log.info(e.getMessage());
                sendAmount = null;
                if (amountParsed == null) {
                    amountError.setText(R.string.amount_error);
                } else if (amountParsed.isNegative()) {
                    amountError.setText(R.string.amount_error_negative);
                } else if (amountParsed.compareTo(coinType.getMinNonDust()) < 0) {
                    String minAmount = GenericUtils.formatValue(coinType, coinType.getMinNonDust());
                    String message = getResources().getString(R.string.amount_error_too_small,
                            minAmount, coinType.getSymbol());
                    amountError.setText(message);
                } else if (pocket != null && amountParsed.compareTo(pocket.getBalance()) > 0) {
                    amountError.setText(R.string.amount_error_not_enough_money);
                } else { // Should not happen, but show a generic error
                    amountError.setText(R.string.amount_error);
                }
                amountError.setVisibility(View.VISIBLE);
            }
        } catch (ArithmeticException e) {
            amountWarning.setVisibility(View.GONE);
            if (!isTyping) {
                log.info(e.getMessage());
                sendAmount = null;
                String message = getResources().getString(R.string.amount_error_decimal_places,
                        coinType.getUnitExponent());
                amountError.setText(message);
                amountError.setVisibility(View.VISIBLE);
            }
        }
        updateView();
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
                address = new Address(coinType, addressStr);
            } else {
                // empty field should not raise error message
                address = null;
            }
            addressError.setVisibility(View.GONE);
        }
        catch (final AddressFormatException x) {
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
        if (state != State.INPUT || pocket == null) return;

        Coin availableBalance = pocket.getBalance(false);
        if (availableBalance.isZero()) {
            Toast.makeText(getActivity(), R.string.amount_error_not_enough_money,
                    Toast.LENGTH_LONG).show();
        } else {
            sendAmountView.setText(GenericUtils.formatValue(coinType, availableBalance));
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
            mListener = (WalletActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + WalletActivity.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private abstract class EditViewListener implements View.OnFocusChangeListener, TextWatcher {
        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) { }
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) { }
    }

    private final class SendAmountListener extends EditViewListener {
        @Override
        public void onFocusChange(final View v, final boolean hasFocus) {
            if (!hasFocus) {
                validateAmount();
            }
        }

        @Override
        public void afterTextChanged(final Editable s) {
            validateAmount(true);
        }
    }

    private final class ReceivingAddressListener extends EditViewListener {
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

    }
}
