package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exceptions.NoSuchPocketException;
import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftAmountTx;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftMarketInfo;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftNormalTx;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftTime;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.util.ExchangeRate;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.ExchangeHistoryProvider;
import com.coinomi.wallet.ExchangeHistoryProvider.ExchangeEntry;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.SendOutput;
import com.coinomi.wallet.ui.widget.TransactionAmountVisualizer;
import com.coinomi.wallet.util.Keyboard;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import javax.annotation.Nullable;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;
import static com.coinomi.wallet.Constants.ARG_ACCOUNT_ID;
import static com.coinomi.wallet.Constants.ARG_EMPTY_WALLET;
import static com.coinomi.wallet.Constants.ARG_SEND_REQUEST;
import static com.coinomi.wallet.Constants.ARG_SEND_TO_ACCOUNT_ID;
import static com.coinomi.wallet.Constants.ARG_SEND_TO_ADDRESS;
import static com.coinomi.wallet.Constants.ARG_SEND_VALUE;
import static com.coinomi.wallet.Constants.ARG_TX_MESSAGE;
import static com.coinomi.wallet.ExchangeRatesProvider.getRates;

/**
 * This fragment displays a busy message and makes the transaction in the background
 *
 */
public class MakeTransactionFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(MakeTransactionFragment.class);

    private static final int START_TRADE_TIMEOUT = 0;
    private static final int UPDATE_TRADE_TIMEOUT = 1;
    private static final int TRADE_EXPIRED = 2;
    private static final int STOP_TRADE_TIMEOUT = 3;

    private static final int SAFE_TIMEOUT_MARGIN_SEC = 60;

    // Loader IDs
    private static final int ID_RATE_LOADER = 0;

    private static final String TRANSACTION_BROADCAST = "transaction_broadcast";
    private static final String ERROR = "error";
    private static final String EXCHANGE_ENTRY = "exchange_entry";
    private static final String DEPOSIT_ADDRESS = "deposit_address";
    private static final String DEPOSIT_AMOUNT = "deposit_amount";
    private static final String WITHDRAW_ADDRESS = "withdraw_address";
    private static final String WITHDRAW_AMOUNT = "withdraw_amount";

    private static final String PREPARE_TRANSACTION_BUSY_DIALOG_TAG = "prepare_transaction_busy_dialog_tag";
    private static final String SIGNING_TRANSACTION_BUSY_DIALOG_TAG = "signing_transaction_busy_dialog_tag";

    private Handler handler = new MyHandler(this);
    @Nullable private String password;
    private Listener listener;
    private ContentResolver contentResolver;
    private SignAndBroadcastTask signAndBroadcastTask;
    private CreateTransactionTask createTransactionTask;
    private WalletApplication application;
    private Configuration config;

    @Nullable AbstractAddress sendToAddress;
    boolean sendingToAccount;
    @Nullable private Value sendAmount;
    boolean emptyWallet;
    private CoinType sourceType;
    private SendRequest request;
    @Nullable private AbstractWallet sourceAccount;
    @Nullable private ExchangeEntry exchangeEntry;
    @Nullable private AbstractAddress tradeDepositAddress;
    @Nullable private Value tradeDepositAmount;
    @Nullable private AbstractAddress tradeWithdrawAddress;
    @Nullable private Value tradeWithdrawAmount;
    @Nullable private TxMessage txMessage;
    private boolean transactionBroadcast = false;
    @Nullable private Exception error;
    private HashMap<String, ExchangeRate> localRates = new HashMap<>();
    private CountDownTimer countDownTimer;

    @Bind(R.id.transaction_info) TextView transactionInfo;
    @Bind(R.id.password) EditText passwordView;
    @Bind(R.id.transaction_amount_visualizer) TransactionAmountVisualizer txVisualizer;
    @Bind(R.id.transaction_trade_withdraw) SendOutput tradeWithdrawSendOutput;

    public static MakeTransactionFragment newInstance(Bundle args) {
        MakeTransactionFragment fragment = new MakeTransactionFragment();
        fragment.setArguments(args);
        return fragment;
    }
    public MakeTransactionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        signAndBroadcastTask = null;

        setRetainInstance(true); // To handle async tasks

        Bundle args = getArguments();
        checkNotNull(args, "Must provide arguments");

        try {
            if (args.containsKey(ARG_SEND_REQUEST)) {
                request = (SendRequest) checkNotNull(args.getSerializable(ARG_SEND_REQUEST));
                checkState(request.isCompleted(), "Only completed requests are currently supported.");
                checkState(request.tx.getSentTo().size() == 1, "Only one output is currently supported");
                sendToAddress = request.tx.getSentTo().get(0).getAddress();
                sourceType = request.type;
                return;
            }

            String fromAccountId = args.getString(ARG_ACCOUNT_ID);
            sourceAccount = (AbstractWallet) checkNotNull(application.getAccount(fromAccountId));
            application.maybeConnectAccount(sourceAccount);
            sourceType = sourceAccount.getCoinType();
            emptyWallet = args.getBoolean(ARG_EMPTY_WALLET, false);
            sendAmount = (Value) args.getSerializable(ARG_SEND_VALUE);
            if (emptyWallet && sendAmount != null) {
                throw new IllegalArgumentException(
                        "Cannot set 'empty wallet' and 'send amount' at the same time");
            }
            if (args.containsKey(ARG_SEND_TO_ACCOUNT_ID)) {
                String toAccountId = args.getString(ARG_SEND_TO_ACCOUNT_ID);
                AbstractWallet toAccount = (AbstractWallet) checkNotNull(application.getAccount(toAccountId));
                sendToAddress = toAccount.getReceiveAddress(config.isManualAddressManagement());
                sendingToAccount = true;
            } else {
                sendToAddress = (AbstractAddress) checkNotNull(args.getSerializable(ARG_SEND_TO_ADDRESS));
                sendingToAccount = false;
            }

            txMessage = (TxMessage) args.getSerializable(ARG_TX_MESSAGE);

            if (savedState != null) {
                error = (Exception) savedState.getSerializable(ERROR);
                transactionBroadcast = savedState.getBoolean(TRANSACTION_BROADCAST);
                exchangeEntry = (ExchangeEntry) savedState.getSerializable(EXCHANGE_ENTRY);
                tradeDepositAddress = (AbstractAddress) savedState.getSerializable(DEPOSIT_ADDRESS);
                tradeDepositAmount = (Value) savedState.getSerializable(DEPOSIT_AMOUNT);
                tradeWithdrawAddress = (AbstractAddress) savedState.getSerializable(WITHDRAW_ADDRESS);
                tradeWithdrawAmount = (Value) savedState.getSerializable(WITHDRAW_AMOUNT);
            }

            maybeStartCreateTransaction();
        } catch (Exception e) {
            error = e;
            if (listener != null) {
                listener.onSignResult(e, null);
            }
        }

        String localSymbol = config.getExchangeCurrencyCode();
        for (ExchangeRatesProvider.ExchangeRate rate : getRates(getActivity(), localSymbol).values()) {
            localRates.put(rate.currencyCodeId, rate.rate);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_make_transaction, container, false);
        ButterKnife.bind(this, view);

        if (error != null) return view;

        transactionInfo.setVisibility(View.GONE);

        final TextView passwordLabelView = (TextView) view.findViewById(R.id.enter_password_label);
        if (sourceAccount != null && sourceAccount.isEncrypted()) {
            passwordView.requestFocus();
            passwordView.setVisibility(View.VISIBLE);
            passwordLabelView.setVisibility(View.VISIBLE);
        } else {
            passwordView.setVisibility(View.GONE);
            passwordLabelView.setVisibility(View.GONE);
        }

        tradeWithdrawSendOutput.setVisibility(View.GONE);
        showTransaction();

        TextView poweredByShapeShift = (TextView) view.findViewById(R.id.powered_by_shapeshift);
        poweredByShapeShift.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.about_shapeshift_title)
                        .setMessage(R.string.about_shapeshift_message)
                        .setPositiveButton(R.string.button_ok, null)
                        .create().show();
            }
        });
        poweredByShapeShift.setVisibility((isExchangeNeeded() ? View.VISIBLE : View.GONE));

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    @OnClick(R.id.button_confirm)
    void onConfirmClick() {
        if (passwordView.isShown()) {
            Keyboard.hideKeyboard(getActivity());
            password = passwordView.getText().toString();
        }
        maybeStartSignAndBroadcast();
    }

    private void showTransaction() {
        if (request != null && txVisualizer != null) {
            txVisualizer.setTransaction(sourceAccount, request.tx);
            if (tradeWithdrawAmount != null && tradeWithdrawAddress != null) {
                tradeWithdrawSendOutput.setVisibility(View.VISIBLE);
                if (sendingToAccount) {
                    tradeWithdrawSendOutput.setSending(false);
                } else {
                    tradeWithdrawSendOutput.setSending(true);
                    tradeWithdrawSendOutput.setLabelAndAddress(tradeWithdrawAddress);
                }
                tradeWithdrawSendOutput.setAmount(GenericUtils.formatValue(tradeWithdrawAmount));
                tradeWithdrawSendOutput.setSymbol(tradeWithdrawAmount.type.getSymbol());
                txVisualizer.getOutputs().get(0).setSendLabel(getString(R.string.trade));
                txVisualizer.hideAddresses(); // Hide exchange address
            }
            updateLocalRates();
        }
    }

    boolean isExchangeNeeded() {
        return !sourceType.equals(sendToAddress.getType());
    }

    private void maybeStartCreateTransaction() {
        if (createTransactionTask == null && !transactionBroadcast && error == null) {
            createTransactionTask = new CreateTransactionTask();
            createTransactionTask.execute();
        } else if (createTransactionTask != null && createTransactionTask.getStatus() == AsyncTask.Status.FINISHED) {
            Dialogs.dismissAllowingStateLoss(getFragmentManager(), PREPARE_TRANSACTION_BUSY_DIALOG_TAG);
        }
    }

    private SendRequest generateSendRequest(AbstractAddress sendTo, boolean emptyWallet,
                                            @Nullable Value amount, @Nullable TxMessage txMessage)
            throws WalletAccount.WalletAccountException {

        SendRequest sendRequest;
        if (emptyWallet) {
            sendRequest = sourceAccount.getEmptyWalletRequest(sendTo);
        } else {
            sendRequest = sourceAccount.getSendToRequest(sendTo, checkNotNull(amount));
        }
        sendRequest.txMessage = txMessage;
        sendRequest.signTransaction = false;
        sourceAccount.completeTransaction(sendRequest);

        return sendRequest;
    }

    private boolean isSendingFromSourceAccount() {
        return isEmptyWallet() || (sendAmount != null && sourceType.equals(sendAmount.type));
    }

    private boolean isEmptyWallet() {
        return emptyWallet && sendAmount == null;
    }

    private void maybeStartSignAndBroadcast() {
        if (signAndBroadcastTask == null && !transactionBroadcast && request != null && error == null) {
            signAndBroadcastTask = new SignAndBroadcastTask();
            signAndBroadcastTask.execute();
        } else if (transactionBroadcast) {
            Dialogs.dismissAllowingStateLoss(getFragmentManager(), SIGNING_TRANSACTION_BUSY_DIALOG_TAG);
            Toast.makeText(getActivity(), R.string.tx_already_broadcast, Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onSignResult(error, exchangeEntry);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(TRANSACTION_BROADCAST, transactionBroadcast);
        outState.putSerializable(ERROR, error);
        if (isExchangeNeeded()) {
            outState.putSerializable(EXCHANGE_ENTRY, exchangeEntry);
            outState.putSerializable(DEPOSIT_ADDRESS, tradeDepositAddress);
            outState.putSerializable(DEPOSIT_AMOUNT, tradeDepositAmount);
            outState.putSerializable(WITHDRAW_ADDRESS, tradeWithdrawAddress);
            outState.putSerializable(WITHDRAW_AMOUNT, tradeWithdrawAmount);
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            listener = (Listener) context;
            contentResolver = context.getContentResolver();
            application = (WalletApplication) context.getApplicationContext();
            config = application.getConfiguration();
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " + Listener.class);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        getLoaderManager().destroyLoader(ID_RATE_LOADER);
        listener = null;
        onStopTradeCountDown();
    }


    void onStartTradeCountDown(int secondsLeft) {
        if (countDownTimer != null) return;

        countDownTimer = new CountDownTimer(secondsLeft * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                handler.sendMessage(handler.obtainMessage(
                        UPDATE_TRADE_TIMEOUT, (int) (millisUntilFinished / 1000)));
            }

            public void onFinish() {
                handler.sendEmptyMessage(TRADE_EXPIRED);
            }
        };

        countDownTimer.start();
    }

    void onStopTradeCountDown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
            handler.removeMessages(START_TRADE_TIMEOUT);
            handler.removeMessages(UPDATE_TRADE_TIMEOUT);
            handler.removeMessages(TRADE_EXPIRED);
        }
    }

    private void onTradeExpired() {
        if (transactionBroadcast) { // Transaction already sent, so the trade is not expired
            return;
        }
        if (transactionInfo.getVisibility() != View.VISIBLE) {
            transactionInfo.setVisibility(View.VISIBLE);
        }
        String errorString = getString(R.string.trade_expired);
        transactionInfo.setText(errorString);

        if (listener != null) {
            error = new Exception(errorString);
            listener.onSignResult(error, null);
        }
    }


    private void onUpdateTradeCountDown(int secondsRemaining) {
        if (transactionInfo.getVisibility() != View.VISIBLE) {
            transactionInfo.setVisibility(View.VISIBLE);
        }

        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;

        Resources res = getResources();
        String timeLeft;

        if (minutes > 0) {
            timeLeft = res.getQuantityString(R.plurals.tx_confirm_timer_minute,
                    minutes, String.format("%d:%02d", minutes, seconds));
        } else {
            timeLeft = res.getQuantityString(R.plurals.tx_confirm_timer_second,
                    seconds, seconds);
        }

        String message = getString(R.string.tx_confirm_timer_message, timeLeft);
        transactionInfo.setText(message);
    }

    /**
     * Makes a call to ShapeShift about the time left for the trade
     *
     * Note: do not call this from the main thread!
     */
    @Nullable
    private static ShapeShiftTime getTimeLeftSync(ShapeShift shapeShift, AbstractAddress address) {
        // Try 3 times
        for (int tries = 1; tries <= 3; tries++) {
            try {
                log.info("Getting time left for: {}", address);
                return shapeShift.getTime(address);
            } catch (Exception e) {
                log.info("Will retry: {}", e.getMessage());
                    /* ignore and retry, with linear backoff */
                try {
                    Thread.sleep(1000 * tries);
                } catch (InterruptedException ie) { /*ignored*/ }
            }
        }
        return null;
    }

    private void updateLocalRates() {
        if (localRates != null) {
            if (txVisualizer != null && localRates.containsKey(sourceType.getSymbol())) {
                txVisualizer.setExchangeRate(localRates.get(sourceType.getSymbol()));
            }

            if (tradeWithdrawAmount != null && localRates.containsKey(tradeWithdrawAmount.type.getSymbol())) {
                ExchangeRate rate = localRates.get(tradeWithdrawAmount.type.getSymbol());
                Value fiatAmount = rate.convert(tradeWithdrawAmount);
                tradeWithdrawSendOutput.setAmountLocal(GenericUtils.formatFiatValue(fiatAmount));
                tradeWithdrawSendOutput.setSymbolLocal(fiatAmount.type.getSymbol());
            }
        }
    }

    private void updateLocalRates(HashMap<String, ExchangeRate> rates) {
        localRates = rates;
        updateLocalRates();
    }


    public interface Listener {
        void onSignResult(@Nullable Exception error, @Nullable ExchangeEntry exchange);
    }

    private final LoaderManager.LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            String localSymbol = config.getExchangeCurrencyCode();
            return new ExchangeRateLoader(getActivity(), config, localSymbol);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                HashMap<String, ExchangeRate> rates = new HashMap<>(data.getCount());
                data.moveToFirst();
                do {
                    ExchangeRatesProvider.ExchangeRate rate = ExchangeRatesProvider.getExchangeRate(data);
                    rates.put(rate.currencyCodeId, rate.rate);
                } while (data.moveToNext());

                updateLocalRates(rates);
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };

    /**
     * The fragment handler
     */
    private static class MyHandler extends WeakHandler<MakeTransactionFragment> {
        public MyHandler(MakeTransactionFragment referencingObject) { super(referencingObject); }

        @Override
        protected void weakHandleMessage(MakeTransactionFragment ref, Message msg) {
            switch (msg.what) {
                case START_TRADE_TIMEOUT:
                    ref.onStartTradeCountDown((int) msg.obj);
                    break;
                case UPDATE_TRADE_TIMEOUT:
                    ref.onUpdateTradeCountDown((int) msg.obj);
                    break;
                case TRADE_EXPIRED:
                    ref.onTradeExpired();
                    break;
                case STOP_TRADE_TIMEOUT:
                    ref.onStopTradeCountDown();
                    break;
            }
        }
    }

    private class CreateTransactionTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            // Show dialog as we need to make network connections
            if (isExchangeNeeded()) {
                Dialogs.ProgressDialogFragment.show(getFragmentManager(),
                        getString(R.string.contacting_exchange),
                        PREPARE_TRANSACTION_BUSY_DIALOG_TAG);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (isExchangeNeeded()) {

                    ShapeShift shapeShift = application.getShapeShift();
                    AbstractAddress refundAddress =
                            sourceAccount.getRefundAddress(config.isManualAddressManagement());

                    // If emptying wallet or the amount is the same type as the source account
                    if (isSendingFromSourceAccount()) {
                        ShapeShiftMarketInfo marketInfo = shapeShift.getMarketInfo(
                                sourceType, sendToAddress.getType());

                        // If no values set, make the call
                        if (tradeDepositAddress == null || tradeDepositAmount == null ||
                                tradeWithdrawAddress == null || tradeWithdrawAmount == null) {
                            ShapeShiftNormalTx normalTx =
                                    shapeShift.exchange(sendToAddress, refundAddress);
                            // TODO, show a retry message
                            if (normalTx.isError) throw new Exception(normalTx.errorMessage);
                            tradeDepositAddress = normalTx.deposit;
                            tradeDepositAmount = sendAmount;
                            tradeWithdrawAddress = sendToAddress;
                            // set tradeWithdrawAmount after we generate the send tx
                        }

                        request = generateSendRequest(tradeDepositAddress, isEmptyWallet(),
                                tradeDepositAmount, txMessage);

                        // The amountSending could be equal to sendAmount or the actual amount if
                        // emptying the wallet
                        Value amountSending = request.tx.getValue(sourceAccount).negate().subtract(request.tx.getFee());
                        tradeWithdrawAmount = marketInfo.rate.convert(amountSending);
                    } else {
                        // If no values set, make the call
                        if (tradeDepositAddress == null || tradeDepositAmount == null ||
                                tradeWithdrawAddress == null || tradeWithdrawAmount == null) {
                            ShapeShiftAmountTx fixedAmountTx =
                                    shapeShift.exchangeForAmount(sendAmount, sendToAddress, refundAddress);
                            // TODO, show a retry message
                            if (fixedAmountTx.isError) throw new Exception(fixedAmountTx.errorMessage);
                            tradeDepositAddress = fixedAmountTx.deposit;
                            tradeDepositAmount = fixedAmountTx.depositAmount;
                            tradeWithdrawAddress = fixedAmountTx.withdrawal;
                            tradeWithdrawAmount = fixedAmountTx.withdrawalAmount;
                        }

                        ShapeShiftTime time = getTimeLeftSync(shapeShift, tradeDepositAddress);
                        if (time != null && !time.isError) {
                            int secondsLeft = time.secondsRemaining - SAFE_TIMEOUT_MARGIN_SEC;
                            handler.sendMessage(handler.obtainMessage(
                                    START_TRADE_TIMEOUT, secondsLeft));
                        } else {
                            throw new Exception(time == null ? "Error getting trade expiration time" : time.errorMessage);
                        }
                        request = generateSendRequest(tradeDepositAddress, false,
                                tradeDepositAmount, txMessage);
                    }
                } else {
                    request = generateSendRequest(sendToAddress, isEmptyWallet(),
                            sendAmount, txMessage);
                }
            } catch (Exception e) {
                error = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (Dialogs.dismissAllowingStateLoss(getFragmentManager(), PREPARE_TRANSACTION_BUSY_DIALOG_TAG)) return;

            if (error != null && listener != null) {
                listener.onSignResult(error, null);
            } else if (error == null) {
                showTransaction();
            } else {
                log.warn("Error occurred while creating transaction", error);
            }
        }
    }

    private class SignAndBroadcastTask extends AsyncTask<Void, Void, Exception> {
        @Override
        protected void onPreExecute() {
            Dialogs.ProgressDialogFragment.show(getFragmentManager(),
                    getString(R.string.preparing_transaction),
                    SIGNING_TRANSACTION_BUSY_DIALOG_TAG);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            Wallet wallet = application.getWallet();
            if (wallet == null) return new NoSuchPocketException("No wallet found.");
            try {
                if (sourceAccount != null) {
                    if (wallet.isEncrypted()) {
                        KeyCrypter crypter = checkNotNull(wallet.getKeyCrypter());
                        request.aesKey = crypter.deriveKey(password);
                    }
                    request.signTransaction = true;
                    sourceAccount.completeAndSignTx(request);
                }

                // Before broadcasting, check if there is an error, like the trade expiration
                if (error != null) throw error;

                if (sourceAccount != null) {
                    if (!sourceAccount.broadcastTxSync(request.tx)) {
                        throw new Exception("Error broadcasting transaction: " + request.tx.getHashAsString());
                    }
                } else {
                    // TODO handle better
                    WalletAccount account =
                            wallet.getAccounts(request.tx.getSentTo().get(0).getAddress()).get(0);
                    if (!account.broadcastTxSync(request.tx)) {
                        throw new Exception("Error broadcasting transaction: " + request.tx.getHashAsString());
                    }
                }

                transactionBroadcast = true;
                if (isExchangeNeeded() && tradeDepositAddress != null && tradeDepositAmount != null) {
                    exchangeEntry = new ExchangeEntry(tradeDepositAddress,
                            tradeDepositAmount, request.tx.getHashAsString());
                    Uri uri = ExchangeHistoryProvider.contentUri(application.getPackageName(),
                            tradeDepositAddress);
                    contentResolver.insert(uri, exchangeEntry.getContentValues());
                }
                handler.sendEmptyMessage(STOP_TRADE_TIMEOUT);
            }
            catch (Exception e) { error = e; }

            return error;
        }

        protected void onPostExecute(final Exception e) {
            if (Dialogs.dismissAllowingStateLoss(getFragmentManager(), SIGNING_TRANSACTION_BUSY_DIALOG_TAG)) return;

            if (e instanceof KeyCrypterException) {
                DialogBuilder.warn(getActivity(), R.string.unlocking_wallet_error_title)
                        .setMessage(R.string.unlocking_wallet_error_detail)
                        .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                listener.onSignResult(e, exchangeEntry);
                            }
                        })
                        .setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                password = null;
                                passwordView.setText(null);
                                signAndBroadcastTask = null;
                                error = null;
                            }
                        })
                        .create().show();
            } else if (listener != null) {
                listener.onSignResult(e, exchangeEntry);
            }
        }
    }
}
