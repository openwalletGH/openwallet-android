package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.view.ActionMode;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.FiatType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;
import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftMarketInfo;
import com.coinomi.core.messages.MessageFactory;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.util.ExchangeRate;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.tasks.MarketInfoPollTask;
import com.coinomi.wallet.ui.widget.AddressView;
import com.coinomi.wallet.ui.widget.AmountEditView;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.UiUtils;
import com.coinomi.wallet.util.WeakHandler;
import com.google.common.base.Charsets;

import org.acra.ACRA;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import javax.annotation.Nullable;

import static android.view.View.GONE;
import static android.view.View.OnClickListener;
import static android.view.View.VISIBLE;
import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.coins.Value.canCompare;
import static com.coinomi.wallet.ExchangeRatesProvider.getRates;
import static com.coinomi.wallet.util.UiUtils.setGone;
import static com.coinomi.wallet.util.UiUtils.setVisible;

/**
 * Fragment that prepares a transaction
 *
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public class SendFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SendFragment.class);

    // the fragment initialization parameters
    private static final int REQUEST_CODE_SCAN = 0;
    private static final int SIGN_TRANSACTION = 1;

    private static final int UPDATE_LOCAL_EXCHANGE_RATES = 0;
    private static final int UPDATE_WALLET_CHANGE = 1;
    private static final int UPDATE_MARKET = 2;

    // Loader IDs
    private static final int ID_RATE_LOADER = 0;
    private static final int ID_RECEIVING_ADDRESS_LOADER = 1;

    // Saved state
    private static final String STATE_ADDRESS = "address";
    private static final String STATE_ADDRESS_CAN_CHANGE_TYPE = "address_can_change_type";
    private static final String STATE_AMOUNT = "amount";
    private static final String STATE_AMOUNT_TYPE = "amount_type";

    Handler handler = new MyHandler(this);

    @Nullable private Value lastBalance; // TODO setup wallet watcher for the latest balance
    private AutoCompleteTextView sendToAddressView;
    private AddressView sendToStaticAddressView;
    private TextView addressError;
    private AmountEditView sendCoinAmountView;
    private CurrencyCalculatorLink amountCalculatorLink;
    private TextView amountError;
    private TextView amountWarning;
    private ImageButton scanQrCodeButton;
    private ImageButton eraseAddressButton;
    private Button txMessageButton;
    private TextView txMessageLabel;
    private EditText txMessageView;
    private TextView txMessageCounter;
    private Button sendConfirmButton;
    private Timer timer;
    private MyMarketInfoPollTask pollTask;
    private ActionMode actionMode;

    private State state = State.INPUT;
    private Address address;
    private boolean addressTypeCanChange;
    private Value sendAmount;
    private CoinType sendAmountType;
    private MessageFactory messageFactory;
    private boolean isTxMessageAdded;
    private boolean isTxMessageValid;
    private WalletApplication application;
    private Listener listener;
    private WalletAccount pocket;
    private Configuration config;
    private ContentResolver resolver;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private LoaderManager loaderManager;
    private ReceivingAddressViewAdapter sendToAddressViewAdapter;
    private Map<String, ExchangeRate> localRates = new HashMap<>();
    private ShapeShiftMarketInfo marketInfo;

    private enum State {
        INPUT, PREPARATION, SENDING, SENT, FAILED
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the an account id.
     *
     * @param accountId the id of an account
     * @return A new instance of fragment WalletSendCoins.
     */
    public static SendFragment newInstance(String accountId) {
        SendFragment fragment = new SendFragment();
        Bundle args = new Bundle();
        args.putSerializable(Constants.ARG_ACCOUNT_ID, accountId);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using a URI.
     *
     * @param uri the payment uri
     * @return A new instance of fragment WalletSendCoins.
     */
    public static SendFragment newInstance(Uri uri) {
        SendFragment fragment = new SendFragment();
        Bundle args = new Bundle();
        args.putString(Constants.ARG_URI, uri.toString());
        fragment.setArguments(args);
        return fragment;
    }



    public SendFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(Constants.ARG_ACCOUNT_ID)) {
                String accountId = args.getString(Constants.ARG_ACCOUNT_ID);
                pocket = checkNotNull(application.getAccount(accountId));
            }

            if (args.containsKey(Constants.ARG_URI)) {
                try {
                    processUri(args.getString(Constants.ARG_URI));
                } catch (CoinURIParseException e) {
                    // TODO handle more elegantly
                    Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                    ACRA.getErrorReporter().handleException(e);
                }
            }
            if (pocket == null) {
                List<WalletAccount> accounts = application.getAllAccounts();
                if (accounts.size() > 0) pocket = accounts.get(0);
                if (pocket == null) {
                    ACRA.getErrorReporter().putCustomData("wallet-exists",
                            application.getWallet() == null ? "no" : "yes");
                    Toast.makeText(getActivity(), R.string.no_such_pocket_error,
                            Toast.LENGTH_LONG).show();
                    getActivity().finish();
                    return;
                }
            }
            checkNotNull(pocket, "No account selected");
        } else {
            throw new RuntimeException("Must provide account ID or a payment URI");
        }

        sendAmountType = pocket.getCoinType();

        messageFactory = pocket.getCoinType().getMessagesFactory();

        if (savedInstanceState != null) {
            address = (Address) savedInstanceState.getSerializable(STATE_ADDRESS);
            addressTypeCanChange = savedInstanceState.getBoolean(STATE_ADDRESS_CAN_CHANGE_TYPE);
            sendAmount = (Value) savedInstanceState.getSerializable(STATE_AMOUNT);
            sendAmountType = (CoinType) savedInstanceState.getSerializable(STATE_AMOUNT_TYPE);
        }

        updateBalance();
        setHasOptionsMenu(true);
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);

        String localSymbol = config.getExchangeCurrencyCode();
        for (ExchangeRatesProvider.ExchangeRate rate : getRates(getActivity(), localSymbol)) {
            localRates.put(rate.currencyCodeId, rate.rate);
        }

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
        loaderManager.initLoader(ID_RECEIVING_ADDRESS_LOADER, null, receivingAddressLoaderCallbacks);
    }

    private void processUri(String uri) throws CoinURIParseException {
        CoinURI coinUri = new CoinURI(uri);
        CoinType scannedType = coinUri.getTypeRequired();

        if (!Constants.SUPPORTED_COINS.contains(scannedType)) {
            String error = getResources().getString(R.string.unsupported_coin, scannedType.getName());
            throw new CoinURIParseException(error);
        }

        if (pocket == null) {
            List<WalletAccount> allAccounts = application.getAllAccounts();
            List<WalletAccount> sendFromAccounts = application.getAccounts(scannedType);
            if (sendFromAccounts.size() == 1) {
                pocket = sendFromAccounts.get(0);
            } else if (allAccounts.size() == 1) {
                pocket = allAccounts.get(0);
            } else {
                throw new CoinURIParseException("No default account found");
            }
        }

        if (coinUri.isAddressRequest()) {
            UiUtils.replyAddressRequest(getActivity(), coinUri, pocket);
        } else {
            setUri(coinUri);
        }
    }

    private void updateBalance() {
        if (pocket != null) {
            lastBalance = pocket.getBalance();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_send, container, false);

        sendToAddressView = (AutoCompleteTextView) view.findViewById(R.id.send_to_address);
        sendToAddressViewAdapter = new ReceivingAddressViewAdapter(application);
        sendToAddressView.setAdapter(sendToAddressViewAdapter);
        sendToAddressView.setOnFocusChangeListener(receivingAddressListener);
        sendToAddressView.addTextChangedListener(receivingAddressListener);

        sendToStaticAddressView = (AddressView) view.findViewById(R.id.send_to_address_static);
        sendToStaticAddressView.setOnClickListener(addressOnClickListener);

        sendCoinAmountView = (AmountEditView) view.findViewById(R.id.send_coin_amount);
        sendCoinAmountView.resetType(sendAmountType);
        if (sendAmount != null) sendCoinAmountView.setAmount(sendAmount, false);

        AmountEditView sendLocalAmountView = (AmountEditView) view.findViewById(R.id.send_local_amount);
        sendLocalAmountView.setFormat(FiatType.FRIENDLY_FORMAT);

        amountCalculatorLink = new CurrencyCalculatorLink(sendCoinAmountView, sendLocalAmountView);
        amountCalculatorLink.setExchangeDirection(config.getLastExchangeDirection());
        amountCalculatorLink.setExchangeRate(getCurrentRate());

        addressError = (TextView) view.findViewById(R.id.address_error_message);
        addressError.setVisibility(View.GONE);
        amountError = (TextView) view.findViewById(R.id.amount_error_message);
        amountError.setVisibility(View.GONE);
        amountWarning = (TextView) view.findViewById(R.id.amount_warning_message);
        amountWarning.setVisibility(View.GONE);

        scanQrCodeButton = (ImageButton) view.findViewById(R.id.scan_qr_code);
        scanQrCodeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleScan();
            }
        });

        eraseAddressButton = (ImageButton) view.findViewById(R.id.erase_address);
        eraseAddressButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAddress(true);
                updateView();
            }
        });

        txMessageButton = (Button) view.findViewById(R.id.tx_message_add_remove);
        txMessageLabel = (TextView) view.findViewById(R.id.tx_message_label);
        txMessageView = (EditText) view.findViewById(R.id.tx_message);
        txMessageCounter = (TextView) view.findViewById(R.id.tx_message_counter);

        if (pocket != null && pocket.getCoinType().canHandleMessages()) {
            enableTxMessage(view);
        }

        sendConfirmButton = (Button) view.findViewById(R.id.send_confirm);
        sendConfirmButton.setOnClickListener(new OnClickListener() {
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

    private void enableTxMessage(View view) {
        if (messageFactory == null) return;

        txMessageButton.setVisibility(View.VISIBLE);
        txMessageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isTxMessageAdded) { // if tx message added, remove it
                    hideTxMessage();
                } else { // else show tx message fields
                    showTxMessage();
                }
            }
        });

        final int maxMessageBytes = messageFactory.maxMessageSizeBytes();
        final int messageLengthThreshold = (int) (maxMessageBytes * .8); // 80% full
        final int txMessageCounterPaddingOriginal = txMessageView.getPaddingBottom();
        final int txMessageCounterPadding =
                getResources().getDimensionPixelSize(R.dimen.tx_message_counter_padding);
        final int colorWarn = getResources().getColor(R.color.fg_warning);
        final int colorError = getResources().getColor(R.color.fg_error);

        // This listener checks the length of the message and displays a counter if it passes a
        // threshold or the max size. It also changes the bottom padding of the message field
        // to accommodate the counter.
        txMessageView.addTextChangedListener(new EditViewListener() {
            @Override public void afterTextChanged(final Editable s) {
                // Not very efficient because it creates a new String object on each key press
                int length = s.toString().getBytes(Charsets.UTF_8).length;
                boolean isTxMessageValidNow = true;
                if (length < messageLengthThreshold) {
                    if (txMessageCounter.getVisibility() != GONE) {
                        txMessageCounter.setVisibility(GONE);
                        txMessageView.setPadding(0, 0, 0, txMessageCounterPaddingOriginal);
                    }
                } else {
                    int remaining = maxMessageBytes - length;
                    if (txMessageCounter.getVisibility() != VISIBLE) {
                        txMessageCounter.setVisibility(VISIBLE);
                        txMessageView.setPadding(0, 0, 0, txMessageCounterPadding);
                    }
                    txMessageCounter.setText(Integer.toString(remaining));
                    if (length <= maxMessageBytes) {
                        txMessageCounter.setTextColor(colorWarn);
                    } else {
                        isTxMessageValidNow = false;
                        txMessageCounter.setTextColor(colorError);
                    }
                }
                // Update view only if the message validity changed
                if (isTxMessageValid != isTxMessageValidNow) {
                    isTxMessageValid = isTxMessageValidNow;
                    updateView();
                }
            }

            @Override public void onFocusChange(final View v, final boolean hasFocus) {
                if (!hasFocus) {
                    validateTxMessage();
                }
            }
        });
    }

    private void showTxMessage() {
        if (messageFactory != null) {
            txMessageButton.setText(R.string.tx_message_public_remove);
            txMessageLabel.setVisibility(View.VISIBLE);
            txMessageView.setVisibility(View.VISIBLE);
            isTxMessageAdded = true;
            isTxMessageValid = true; // Initially the empty message is valid, even if it is ignored
        }
    }

    private void hideTxMessage() {
        if (messageFactory != null) {
            txMessageButton.setText(R.string.tx_message_public_add);
            txMessageLabel.setVisibility(View.GONE);
            txMessageView.setText(null);
            txMessageView.setVisibility(View.GONE);
            isTxMessageAdded = false;
            isTxMessageValid = false;
        }
    }

    private void clearAddress(boolean clearTextField) {
        address = null;
        if (clearTextField) setSendToAddressText(null);
        sendAmountType = pocket.getCoinType();
        addressTypeCanChange = false;
    }

    private void setAddress(Address address, boolean typeCanChange) {
        this.address = address;
        this.addressTypeCanChange = typeCanChange;
    }

    @Override
    public void onDestroyView() {
        config.setLastExchangeDirection(amountCalculatorLink.getExchangeDirection());
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        loaderManager.destroyLoader(ID_RECEIVING_ADDRESS_LOADER);
        loaderManager.destroyLoader(ID_RATE_LOADER);

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        amountCalculatorLink.setListener(amountsListener);

        resolver.registerContentObserver(AddressBookProvider.contentUri(
                getActivity().getPackageName()), true, addressBookObserver);

        if (pocket != null) {
            pocket.addEventListener(transactionChangeListener, Threading.SAME_THREAD);
        }

        updateBalance();
        updateView();
    }

    @Override
    public void onPause() {
        if (pocket != null) pocket.removeEventListener(transactionChangeListener);
        transactionChangeListener.removeCallbacks();

        resolver.unregisterContentObserver(addressBookObserver);

        amountCalculatorLink.setListener(null);

        stopPolling();

        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATE_ADDRESS, address);
        outState.putBoolean(STATE_ADDRESS_CAN_CHANGE_TYPE, addressTypeCanChange);
        outState.putSerializable(STATE_AMOUNT, sendAmount);
        outState.putSerializable(STATE_AMOUNT_TYPE, sendAmountType);
    }

    private void startOrStopMarketRatePolling() {
        if (address != null && !pocket.isType(address)) {
            String pair = ShapeShift.getPair(pocket.getCoinType(), (CoinType) address.getParameters());
            if (timer == null) {
                startPolling(pair);
            } else {
                pollTask.updatePair(pair);
            }
        } else if (timer != null) {
            stopPolling();
        }
    }

    /**
     * Start polling for the market information of the current pair, if it is already stated this
     * call does nothing
     */
    private void startPolling(String pair) {
        if (timer == null) {
            ShapeShift shapeShift = application.getShapeShift();
            pollTask = new MyMarketInfoPollTask(handler, shapeShift, pair);
            timer = new Timer();
            timer.schedule(pollTask, 0, Constants.RATE_UPDATE_FREQ_MS);
        }
    }

    /**
     * Stop the polling for the market info, if it is already stop this call does nothing
     */
    private void stopPolling() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
            pollTask.cancel();
            pollTask = null;
        }
    }

    private void handleScan() {
        startActivityForResult(new Intent(getActivity(), ScanActivity.class), REQUEST_CODE_SCAN);
    }

    private void handleSendConfirm() {
        if (!everythingValid()) { // Sanity check
            log.error("Unexpected validity failure.");
            validateAmount();
            validateAddress();
            validateTxMessage();
            return;
        }
        state = State.PREPARATION;
        updateView();
        if (application.getWallet() != null) {
            onMakeTransaction(address, sendAmount, getTxMessage());
        }
    }

    @Nullable
    private TxMessage getTxMessage() {
        if (isTxMessageAdded && messageFactory != null && txMessageView.getText().length() != 0) {
            String message = txMessageView.getText().toString();
            try {
                return messageFactory.createPublicMessage(message);
            } catch (Exception e) { // Should not happen
                ACRA.getErrorReporter().handleSilentException(e);
            }
        }
        return null;
    }

    public void onMakeTransaction(Address toAddress, Value amount, @Nullable TxMessage txMessage) {
        Intent intent = new Intent(getActivity(), SignTransactionActivity.class);

        // Decide if emptying wallet or not
        if (canCompare(lastBalance, amount) && amount.compareTo(lastBalance) == 0) {
            intent.putExtra(Constants.ARG_EMPTY_WALLET, true);
        } else {
            intent.putExtra(Constants.ARG_SEND_VALUE, amount);
        }
        intent.putExtra(Constants.ARG_ACCOUNT_ID, pocket.getId());
        intent.putExtra(Constants.ARG_SEND_TO_ADDRESS, toAddress);
        if (txMessage != null) intent.putExtra(Constants.ARG_TX_MESSAGE, txMessage);

        startActivityForResult(intent, SIGN_TRANSACTION);
        state = State.INPUT;
    }

    public void reset() {
        // No-op if the view is not created
        if (getView() == null) return;

        clearAddress(true);
        hideTxMessage();
        sendToAddressView.setVisibility(View.VISIBLE);
        sendToStaticAddressView.setVisibility(View.GONE);
        amountCalculatorLink.setPrimaryAmount(null);
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
                String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                if (!processInput(input)) {
                    String error = getResources().getString(R.string.scan_error, input);
                    Toast.makeText(getActivity(), error, Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == SIGN_TRANSACTION) {
            if (resultCode == Activity.RESULT_OK) {
                Exception error = (Exception) intent.getSerializableExtra(Constants.ARG_ERROR);

                if (error == null) {
                    Toast.makeText(getActivity(), R.string.sending_msg, Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onTransactionBroadcastSuccess(pocket, null);
                } else {
                    if (error instanceof InsufficientMoneyException) {
                        Toast.makeText(getActivity(), R.string.amount_error_not_enough_money_plain, Toast.LENGTH_LONG).show();
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
                    if (listener != null) listener.onTransactionBroadcastFailure(pocket, null);
                }
            }
        }
    }

    private boolean processInput(String input) {
        input = input.trim();
        try {
            updateStateFrom(new CoinURI(input));
            return true;
        } catch (final CoinURIParseException x) {
            try {
                parseAddress(input);
                updateView();
                return true;
            } catch (AddressFormatException e) {
                return false;
            }
        }
    }

    public void updateStateFrom(CoinURI coinUri) throws CoinURIParseException {
        // No-op if the view is not created
        if (getView() == null) return;

        if (coinUri.isAddressRequest() && coinUri.getTypeRequired().equals(pocket.getCoinType())) {
            UiUtils.replyAddressRequest(getActivity(), coinUri, pocket);
            return;
        }

        setUri(coinUri);

        // delay these actions until fragment is resumed
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (sendAmount != null) {
                    amountCalculatorLink.setPrimaryAmount(sendAmount);
                }
                updateView();
                validateEverything();
                requestFocusFirst();
            }
        });
    }

    private void setUri(CoinURI coinUri) throws CoinURIParseException {
        setAddress(coinUri.getAddress(), false);
        if (address == null) { // TODO when going to support the payment protocol, address could be null
            throw new CoinURIParseException("missing address");
        }

        sendAmountType = (CoinType) address.getParameters();
        sendAmount = coinUri.getAmount();
        final String label = coinUri.getLabel();
    }

    private void updateView() {
        sendConfirmButton.setEnabled(everythingValid());

        if (address == null) {
            setVisible(sendToAddressView);
            setGone(sendToStaticAddressView);
            setVisible(scanQrCodeButton);
            setGone(eraseAddressButton);

            if (actionMode != null) {
                actionMode.finish();
                actionMode = null;
            }
        } else {
            setGone(sendToAddressView);
            setVisible(sendToStaticAddressView);
            sendToStaticAddressView.setAddressAndLabel(address);
            setGone(scanQrCodeButton);
            setVisible(eraseAddressButton);
        }

        if (sendCoinAmountView.resetType(sendAmountType)) {
            amountCalculatorLink.setExchangeRate(getCurrentRate());
        }

        startOrStopMarketRatePolling();

        // enable actions
        scanQrCodeButton.setEnabled(state == State.INPUT);
        eraseAddressButton.setEnabled(state == State.INPUT);
    }

    private boolean isTxMessageValid() {
        return isTxMessageAdded && isTxMessageValid;
    }

    private boolean isOutputsValid() {
        return address != null;
    }

    private boolean isAmountValid() {
        return isAmountValid(sendAmount);
    }

    private boolean isAmountValid(Value amount) {
        return amount != null && isAmountWithinLimits(amount);
    }

    /**
     * Check if amount is within the minimum and maximum deposit limits and if is dust or if is more
     * money than currently in the wallet
     */
    private boolean isAmountWithinLimits(Value amount) {
        boolean isWithinLimits = amount != null && amount.isPositive() && !amount.isDust();

        // Check if within min & max deposit limits
        if (isWithinLimits && marketInfo != null && canCompare(marketInfo.limit, amount)) {
            isWithinLimits = amount.within(marketInfo.minimum, marketInfo.limit);
        }

        // Check if we have the amount
        if (isWithinLimits && canCompare(lastBalance, amount)) {
            isWithinLimits = amount.compareTo(lastBalance) <= 0;
        }

        return isWithinLimits;
    }

    /**
     * Check if amount is smaller than the dust limit or if applicable, the minimum deposit.
     */
    private boolean isAmountTooSmall(Value amount) {
        return amount.compareTo(getLowestAmount(amount.type)) < 0;
    }

    /**
     * Get the lowest deposit or withdraw for the provided amount type
     */
    private Value getLowestAmount(ValueType type) {
        Value min = type.minNonDust();
        if (marketInfo != null) {
            if (marketInfo.minimum.isOfType(min)) {
                min = Value.max(marketInfo.minimum, min);
            } else if (marketInfo.rate.canConvert(type, marketInfo.minimum.type)) {
                min = Value.max(marketInfo.rate.convert(marketInfo.minimum), min);
            }
        }
        return min;
    }



    private boolean everythingValid() {
        return state == State.INPUT && isOutputsValid() && isAmountValid() &&
                (!isTxMessageAdded || isTxMessageValid());
    }

    private void requestFocusFirst() {
        if (!isOutputsValid()) {
            sendToAddressView.requestFocus();
        } else if (!isAmountValid()) {
            amountCalculatorLink.requestFocus();
            // FIXME causes problems in older Androids
//            Keyboard.focusAndShowKeyboard(sendAmountView, getActivity());
        } else if (isTxMessageAdded && !isTxMessageValid()) {
            txMessageView.requestFocus();
        } else if (everythingValid()) {
            sendConfirmButton.requestFocus();
        } else {
            log.warn("unclear focus");
        }
    }

    private void validateEverything() {
        validateAddress();
        validateAmount();
        validateTxMessage();
    }

    private void validateTxMessage() {
        if (isTxMessageAdded && messageFactory != null && txMessageView != null) {
            int messageBytes = txMessageView.getText().toString().getBytes(Charsets.UTF_8).length;
            isTxMessageValid = messageBytes <= messageFactory.maxMessageSizeBytes();
            updateView();
        }
    }

    private void validateAmount() {
        validateAmount(false);
    }

    private void validateAmount(boolean isTyping) {
        Value amountParsed = amountCalculatorLink.getPrimaryAmount();

        if (isAmountValid(amountParsed)) {
            sendAmount = amountParsed;
            amountError.setVisibility(View.GONE);
            // Show warning that fees apply when entered the full amount inside the pocket
            if (canCompare(sendAmount, lastBalance) && sendAmount.compareTo(lastBalance) == 0) {
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
                } else if (!isAmountWithinLimits(amountParsed)) {
                    String message = getString(R.string.error_generic);
                    // If the amount is dust or lower than the deposit limit
                    if (isAmountTooSmall(amountParsed)) {
                        Value minAmount = getLowestAmount(amountParsed.type);
                        message = getString(R.string.amount_error_too_small,
                                minAmount.toFriendlyString());
                    } else {
                        // If we have the amount
                        if (canCompare(lastBalance, amountParsed) &&
                                amountParsed.compareTo(lastBalance) > 0) {
                            message = getString(R.string.amount_error_not_enough_money,
                                    lastBalance.toFriendlyString());
                        }

                        if (marketInfo != null && canCompare(marketInfo.limit, amountParsed) &&
                                amountParsed.compareTo(marketInfo.limit) > 0) {
                            message = getString(R.string.trade_error_max_limit,
                                    marketInfo.limit.toFriendlyString());
                        }
                    }
                    amountError.setText(message);
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
     * Decide if should show errors in the UI.
     */
    private boolean shouldShowErrors(boolean isTyping, Value amount) {
        if (amount != null && !amount.isZero() && !isAmountWithinLimits(amount)) {
            return true;
        }

        if (isTyping) return false;
        if (amountCalculatorLink.isEmpty()) return false;
        if (amount != null && amount.isZero()) return false;

        return true;
    }

    private void validateAddress() {
        validateAddress(false);
    }

    private void validateAddress(boolean isTyping) {
        if (address == null) {
            String input = sendToAddressView.getText().toString().trim();

            try {
                if (!input.isEmpty()) {
                    // Process fast the input string
                    if (processInput(input)) return;

                    // Try to fix address if needed
                    parseAddress(GenericUtils.fixAddress(input));
                } else {
                    // empty field should not raise error message
                    clearAddress(false);
                }
                addressError.setVisibility(View.GONE);
            } catch (final AddressFormatException x) {
                // could not decode address at all
                if (!isTyping) {
                    clearAddress(false);
                    addressError.setText(R.string.address_error);
                    addressError.setVisibility(View.VISIBLE);
                }
            }
            updateView();
        }
    }

    private void setSendToAddressText(String addressStr) {
        // Remove listener before changing input, to avoid infinite recursion
        sendToAddressView.removeTextChangedListener(receivingAddressListener);
        sendToAddressView.setOnFocusChangeListener(null);
        sendToAddressView.setText(addressStr);
        sendToAddressView.addTextChangedListener(receivingAddressListener);
        sendToAddressView.setOnFocusChangeListener(receivingAddressListener);
    }

    private void parseAddress(String addressStr) throws AddressFormatException {
        List<CoinType> possibleTypes = GenericUtils.getPossibleTypes(addressStr);
        if (possibleTypes.contains(pocket.getCoinType())) {
            setAddress(new Address(pocket.getCoinType(), addressStr), true);
            sendAmountType = pocket.getCoinType();
        } else if (possibleTypes.size() == 1) {
            setAddress(new Address(possibleTypes.get(0), addressStr), true);
            sendAmountType = possibleTypes.get(0);
        } else {
            // This address string could be more that one coin type so first check if this address
            // comes from an account to determine the type.
            List<WalletAccount> possibleAccounts = application.getAccounts(possibleTypes);
            Address addressOfAccount = null;
            for (WalletAccount account : possibleAccounts) {
                Address testAddress = new Address(account.getCoinType(), addressStr);
                if (account.isAddressMine(testAddress)) {
                    addressOfAccount = testAddress;
                    break;
                }
            }

            if (addressOfAccount != null) {
                // If address is from an account don't show a dialog. The type should not change as
                // we know 100% that is correct one
                setAddress(addressOfAccount, false);
                sendAmountType = (CoinType) addressOfAccount.getParameters();
            } else {
                // As a last resort let the use choose the correct coin type
                showPayToDialog(addressStr);
            }
        }
    }

    private void showPayToDialog(String addressStr) {
        if (!isVisible() || !isResumed()) return;
        if (selectCoinTypeDialog.getArguments() == null) {
            selectCoinTypeDialog.setArguments(new Bundle());
        }
        selectCoinTypeDialog.getArguments().putString(Constants.ARG_ADDRESS_STRING, addressStr);
        selectCoinTypeDialog.show(getFragmentManager(), null);
    }

    SelectCoinTypeDialog selectCoinTypeDialog = new SelectCoinTypeDialog() {
        // FIXME crash when this dialog being restored from saved state
        @Override
        public void onAddressSelected(Address selectedAddress) {
            setAddress(selectedAddress, true);
            sendAmountType = (CoinType) selectedAddress.getParameters();
            updateView();
        }
    };

    private void setAmountForEmptyWallet() {
        updateBalance();
        if (state != State.INPUT || pocket == null || lastBalance == null) return;

        if (lastBalance.isZero()) {
            String message = getResources().getString(R.string.amount_error_not_enough_money_plain);
            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
        } else {
            amountCalculatorLink.setPrimaryAmount(lastBalance);
            validateAmount();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mNavigationDrawerFragment != null && !mNavigationDrawerFragment.isDrawerOpen()) {
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
            this.listener = (Listener) activity;
            this.application = (WalletApplication) activity.getApplication();
            this.config = application.getConfiguration();
            this.resolver = activity.getContentResolver();
            this.loaderManager = getLoaderManager();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface Listener {
        public void onTransactionBroadcastSuccess(WalletAccount pocket, Transaction transaction);
        public void onTransactionBroadcastFailure(WalletAccount pocket, Transaction transaction);
    }

    private abstract class EditViewListener implements View.OnFocusChangeListener, TextWatcher {
        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        }
    }

    OnClickListener addressOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (address != null) {
                final boolean showChangeType = addressTypeCanChange &&
                        GenericUtils.hasMultipleTypes(address);
                ActionMode.Callback callback = new UiUtils.AddressActionModeCallback(
                        address, application.getApplicationContext(), getFragmentManager()) {

                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        mode.getMenuInflater().inflate(R.menu.address_options_extra, menu);
                        return super.onCreateActionMode(mode, menu);
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        menu.findItem(R.id.action_change_address_type).setVisible(showChangeType);
                        return super.onPrepareActionMode(mode, menu);
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.action_change_address_type:
                                showPayToDialog(getAddress().toString());
                                mode.finish();
                                return true;
                        }
                        return super.onActionItemClicked(mode, menuItem);
                    }
                };
                actionMode = UiUtils.startActionMode(getActivity(), callback);
                // Hack to dismiss this action mode when back is pressed
                if (listener != null && listener instanceof WalletActivity) {
                    ((WalletActivity) listener).registerActionMode(actionMode);
                }
            }
        }
    };

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
            return new ExchangeRateLoader(getActivity(), config, localSymbol);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                HashMap<String, com.coinomi.core.util.ExchangeRate> rates = new HashMap<>(data.getCount());
                data.moveToFirst();
                do {
                    ExchangeRatesProvider.ExchangeRate rate = ExchangeRatesProvider.getExchangeRate(data);
                    rates.put(rate.currencyCodeId, rate.rate);
                } while (data.moveToNext());
                handler.sendMessage(handler.obtainMessage(UPDATE_LOCAL_EXCHANGE_RATES, rates));
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) { }
    };

    private void onLocalExchangeRatesUpdate(HashMap<String, ExchangeRate> rates) {
        localRates = rates;
        if (state == State.INPUT) {
            amountCalculatorLink.setExchangeRate(getCurrentRate());
        }
    }

    /**
     * Updates the exchange rate and limits for the specific market.
     * Note: if the current pair is different that the marketInfo pair, do nothing
     */
    private void onMarketUpdate(ShapeShiftMarketInfo marketInfo) {
        if (address != null && marketInfo.isPair(pocket.getCoinType(),
                (CoinType) address.getParameters())) {
            this.marketInfo = marketInfo;
        }
    }

    @Nullable
    private ExchangeRate getCurrentRate() {
        return localRates.get(sendAmountType.getSymbol());
    }

    private void onWalletUpdate() {
        updateBalance();
        validateAmount();
    }

    private static class MyHandler extends WeakHandler<SendFragment> {
        public MyHandler(SendFragment referencingObject) { super(referencingObject); }

        @Override
        protected void weakHandleMessage(SendFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_LOCAL_EXCHANGE_RATES:
                    ref.onLocalExchangeRatesUpdate((HashMap<String, ExchangeRate>) msg.obj);
                    break;
                case UPDATE_WALLET_CHANGE:
                    ref.onWalletUpdate();
                    break;
                case UPDATE_MARKET:
                    ref.onMarketUpdate((ShapeShiftMarketInfo) msg.obj);
                    break;
            }
        }
    }

    private final ThrottlingWalletChangeListener transactionChangeListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            handler.sendMessage(handler.obtainMessage(UPDATE_WALLET_CHANGE));
        }
    };

    private final LoaderCallbacks<Cursor> receivingAddressLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            final String constraint = args != null ? args.getString("constraint") : null;
            // TODO support addresses from other accounts
            Uri uri = AddressBookProvider.contentUri(application.getPackageName(), pocket.getCoinType());
            return new CursorLoader(application, uri, null, AddressBookProvider.SELECTION_QUERY,
                    new String[]{constraint != null ? constraint : ""}, null);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> cursor, final Cursor data) {
            sendToAddressViewAdapter.swapCursor(data);
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> cursor) {
            sendToAddressViewAdapter.swapCursor(null);
        }
    };

    private final class ReceivingAddressViewAdapter extends CursorAdapter implements FilterQueryProvider {
        public ReceivingAddressViewAdapter(final Context context) {
            super(context, null, false);
            setFilterQueryProvider(this);
        }

        @Override
        public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            return inflater.inflate(R.layout.address_book_row, parent, false);
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor) {
            final String label = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_LABEL));
            final String address = cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));

            final ViewGroup viewGroup = (ViewGroup) view;
            final TextView labelView = (TextView) viewGroup.findViewById(R.id.address_book_row_label);
            labelView.setText(label);
            final TextView addressView = (TextView) viewGroup.findViewById(R.id.address_book_row_address);
            addressView.setText(GenericUtils.addressSplitToGroupsMultiline(address));
        }

        @Override
        public CharSequence convertToString(final Cursor cursor) {
            return cursor.getString(cursor.getColumnIndexOrThrow(AddressBookProvider.KEY_ADDRESS));
        }

        @Override
        public Cursor runQuery(final CharSequence constraint) {
            final Bundle args = new Bundle();
            if (constraint != null)
                args.putString("constraint", constraint.toString());
            loaderManager.restartLoader(ID_RECEIVING_ADDRESS_LOADER, args, receivingAddressLoaderCallbacks);
            return getCursor();
        }
    }

    private static class MyMarketInfoPollTask extends MarketInfoPollTask {
        private final Handler handler;

        MyMarketInfoPollTask(Handler handler, ShapeShift shapeShift, String pair) {
            super(shapeShift, pair);
            this.handler = handler;
        }

        @Override
        public void onHandleMarketInfo(ShapeShiftMarketInfo marketInfo) {
            handler.sendMessage(handler.obtainMessage(UPDATE_MARKET, marketInfo));
        }
    }

    private final ContentObserver addressBookObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            updateView();
        }
    };
}
