package com.coinomi.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
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

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftAmountTx;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftMarketInfo;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftNormalTx;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftTime;
import com.coinomi.core.util.ExchangeRate;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletPocketHD;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.ExchangeHistoryProvider;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeHistoryProvider.ExchangeEntry;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.SendOutput;
import com.coinomi.wallet.ui.widget.TransactionAmountVisualizer;
import com.coinomi.wallet.util.Keyboard;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.crypto.KeyCrypter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.wallet.Constants.ARG_ACCOUNT_ID;
import static com.coinomi.wallet.Constants.ARG_EMPTY_WALLET;
import static com.coinomi.wallet.Constants.ARG_SEND_TO_ACCOUNT_ID;
import static com.coinomi.wallet.Constants.ARG_SEND_TO_ADDRESS;
import static com.coinomi.wallet.Constants.ARG_SEND_VALUE;

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

    private static final int SAFE_TIMEOUT_MARGIN = 30;

    // Loader IDs
    private static final int ID_RATE_LOADER = 0;

    private static final String DEPOSIT_ADDRESS = "deposit_address";
    private static final String DEPOSIT_AMOUNT = "deposit_amount";
    private static final String WITHDRAW_ADDRESS = "withdraw_address";
    private static final String WITHDRAW_AMOUNT = "withdraw_amount";

    private Handler handler = new MyHandler(this);
    @Nullable private String password;
    private Listener mListener;
    private ContentResolver contentResolver;
    private SignAndBroadcastTask signAndBroadcastTask;
    private CreateTransactionTask createTransactionTask;
    private WalletApplication application;
    private Configuration config;
    private TextView transactionInfo;
    private TransactionAmountVisualizer txVisualizer;
    private SendOutput tradeWithdrawSendOutput;
    private Address sendToAddress;
    @Nullable private Value sendAmount;
    private boolean emptyWallet;
    private CoinType sourceType;
    private SendRequest request;
    private LoaderManager loaderManager;
    private WalletPocketHD sourceAccount;
    @Nullable private ExchangeEntry exchangeEntry;
    @Nullable private Address tradeDepositAddress;
    @Nullable private Value tradeDepositAmount;
    @Nullable private Address tradeWithdrawAddress;
    @Nullable private Value tradeWithdrawAmount;
    private boolean transactionBroadcast = false;
    @Nullable private Exception error;

    private CountDownTimer countDownTimer;


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

        Bundle args = getArguments();
        checkNotNull(args, "Must provide arguments");

        try {
            String fromAccountId = args.getString(ARG_ACCOUNT_ID);
            sourceAccount = (WalletPocketHD) checkNotNull(application.getAccount(fromAccountId));
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
                WalletPocketHD toAccount = (WalletPocketHD) checkNotNull(application.getAccount(toAccountId));
                sendToAddress = toAccount.getReceiveAddress(config.isManualAddressManagement());
            } else {
                sendToAddress = (Address) checkNotNull(args.getSerializable(ARG_SEND_TO_ADDRESS));
            }

            if (savedState != null) {
                tradeDepositAddress = (Address) savedState.getSerializable(DEPOSIT_ADDRESS);
                tradeDepositAmount = (Value) savedState.getSerializable(DEPOSIT_AMOUNT);
                tradeWithdrawAddress = (Address) savedState.getSerializable(WITHDRAW_ADDRESS);
                tradeWithdrawAmount = (Value) savedState.getSerializable(WITHDRAW_AMOUNT);
            }

            maybeStartCreateTransaction();
        } catch (Exception e) {
            error = e;
            if (mListener != null) {
                mListener.onSignResult(e);
            }
        }

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onDestroy() {
        loaderManager.destroyLoader(ID_RATE_LOADER);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_make_transaction, container, false);

        if (error != null) return view;

        transactionInfo = (TextView) view.findViewById(R.id.transaction_info);
        transactionInfo.setVisibility(View.GONE);

        final EditText passwordView = (EditText) view.findViewById(R.id.password);
        final TextView passwordLabelView = (TextView) view.findViewById(R.id.enter_password_label);
        if (sourceAccount.isEncrypted()) {
            passwordView.requestFocus();
            passwordView.setVisibility(View.VISIBLE);
            passwordLabelView.setVisibility(View.VISIBLE);
        } else {
            passwordView.setVisibility(View.GONE);
            passwordLabelView.setVisibility(View.GONE);
        }

        txVisualizer = (TransactionAmountVisualizer) view.findViewById(R.id.transaction_amount_visualizer);
        tradeWithdrawSendOutput = (SendOutput) view.findViewById(R.id.transaction_trade_withdraw);
        tradeWithdrawSendOutput.setVisibility(View.GONE);
        showTransaction();

        view.findViewById(R.id.button_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (passwordView.isShown()) {
                    Keyboard.hideKeyboard(getActivity());
                    password = passwordView.getText().toString();
                }
                maybeStartSignAndBroadcast();
            }
        });

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

    private void showTransaction() {
        if (request != null && txVisualizer != null) {
            txVisualizer.setTransaction(sourceAccount, request.tx);
            if (tradeWithdrawAmount != null && tradeWithdrawAddress != null) {
//                String address = tradeWithdrawAddress.toString();
//                CoinType type = (CoinType) tradeWithdrawAddress.getParameters();
//                String label = AddressBookProvider.resolveLabel(getActivity(), type, address);
                tradeWithdrawSendOutput.setVisibility(View.VISIBLE);
                tradeWithdrawSendOutput.setSending(false);
//                tradeWithdrawSendOutput.setLabelAndAddress(label, address);
                tradeWithdrawSendOutput.setAmount(GenericUtils.formatValue(tradeWithdrawAmount));
                tradeWithdrawSendOutput.setSymbol(tradeWithdrawAmount.type.getSymbol());
                txVisualizer.getOutputs().get(0).setSendLabel(getString(R.string.trade));
                txVisualizer.hideAddresses();
            }
        }
    }

    boolean isExchangeNeeded() {
        return !sourceAccount.getCoinType().equals(sendToAddress.getParameters());
    }

    private void maybeStartCreateTransaction() {
        if (createTransactionTask == null && error == null) {
            createTransactionTask = new CreateTransactionTask();
            createTransactionTask.execute();
        }
    }

    private SendRequest generateSendRequest(Address sendTo,
                                            boolean emptyWallet, @Nullable Value amount)
            throws InsufficientMoneyException {

        SendRequest sendRequest;
        if (emptyWallet) {
            sendRequest = SendRequest.emptyWallet(sendTo);
        } else {
            sendRequest = SendRequest.to(sendTo, checkNotNull(amount).toCoin());
        }
        sendRequest.signInputs = false;
        sourceAccount.completeTx(sendRequest);

        return sendRequest;
    }

    private boolean isSendingFromSourceAccount() {
        return isEmptyWallet() || (sendAmount != null && sourceType.equals(sendAmount.type));
    }

    private boolean isEmptyWallet() {
        return emptyWallet && sendAmount == null;
    }

    private void maybeStartSignAndBroadcast() {
        if (signAndBroadcastTask == null && request != null && error == null) {
            signAndBroadcastTask = new SignAndBroadcastTask();
            signAndBroadcastTask.execute();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (isExchangeNeeded()) {
            outState.putSerializable(DEPOSIT_ADDRESS, tradeDepositAddress);
            outState.putSerializable(DEPOSIT_AMOUNT, tradeDepositAmount);
            outState.putSerializable(WITHDRAW_ADDRESS, tradeWithdrawAddress);
            outState.putSerializable(WITHDRAW_AMOUNT, tradeWithdrawAmount);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (Listener) activity;
            contentResolver = activity.getContentResolver();
            application = (WalletApplication) activity.getApplication();
            config = application.getConfiguration();
            loaderManager = getLoaderManager();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + MakeTransactionFragment.Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
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

        if (mListener != null) {
            error = new Exception(errorString);
            mListener.onSignResult(error);
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
    private static ShapeShiftTime getTimeLeftSync(ShapeShift shapeShift, Address address) {
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


    public interface Listener {
        void onSignResult(@Nullable Exception error);

        /**
         * This method is called when a trade is started and no error occurred
         */
        void onTradeDeposit(ExchangeEntry exchangeEntry);
    }

    private final LoaderManager.LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        String coinSymbol;

        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            String localSymbol = config.getExchangeCurrencyCode();
            coinSymbol = sourceType.getSymbol();
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

                if (txVisualizer != null && rates.containsKey(coinSymbol)) {
                    txVisualizer.setExchangeRate(rates.get(coinSymbol));
                }

                if (tradeWithdrawAmount != null && rates.containsKey(tradeWithdrawAmount.type.getSymbol())) {
                    ExchangeRate rate = rates.get(tradeWithdrawAmount.type.getSymbol());
                    Value fiatAmount = rate.convert(tradeWithdrawAmount);
                    tradeWithdrawSendOutput.setAmountLocal(GenericUtils.formatFiatValue(fiatAmount));
                    tradeWithdrawSendOutput.setSymbolLocal(fiatAmount.type.getSymbol());
                }
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
        private Dialogs.ProgressDialogFragment busyDialog;

        @Override
        protected void onPreExecute() {
            // Show dialog as we need to make network connections
            if (isExchangeNeeded()) {
                busyDialog = Dialogs.ProgressDialogFragment.newInstance(
                        getString(R.string.contacting_exchange));
                busyDialog.show(getFragmentManager(), null);
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (isExchangeNeeded()) {

                    ShapeShift shapeShift = application.getShapeShift();
                    Address refundAddress =
                            sourceAccount.getRefundAddress(config.isManualAddressManagement());

                    // If emptying wallet or the amount is the same type as the source account
                    if (isSendingFromSourceAccount()) {
                        ShapeShiftMarketInfo marketInfo = shapeShift.getMarketInfo(
                                sourceType, (CoinType) sendToAddress.getParameters());

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

                        request = generateSendRequest(tradeDepositAddress, isEmptyWallet(), tradeDepositAmount);

                        // The amountSending could be equal to sendAmount or the actual amount if
                        // emptying the wallet
                        Value amountSending = Value.valueOf(sourceType, request.tx
                                .getValue(sourceAccount).negate() .subtract(request.tx.getFee()));
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
                            int secondsLeft = time.secondsRemaining - SAFE_TIMEOUT_MARGIN;
                            handler.sendMessage(handler.obtainMessage(
                                    START_TRADE_TIMEOUT, secondsLeft));
                        } else {
                            throw new Exception(time == null ? "Error getting trade expiration time" : time.errorMessage);
                        }
                        request = generateSendRequest(tradeDepositAddress, false, tradeDepositAmount);
                    }
                } else {
                    request = generateSendRequest(sendToAddress, isEmptyWallet(), sendAmount);
                }
            } catch (Exception e) {
                error = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (busyDialog != null) busyDialog.dismissAllowingStateLoss();
            if (error != null && mListener != null) {
                mListener.onSignResult(error);
            } else if (error == null) {
                showTransaction();
            } else {
                log.warn("Error occurred while creating transaction", error);
            }
        }
    }

    private class SignAndBroadcastTask extends AsyncTask<Void, Void, Exception> {
        private Dialogs.ProgressDialogFragment busyDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            busyDialog = Dialogs.ProgressDialogFragment.newInstance(
                    getResources().getString(R.string.preparing_transaction));
            busyDialog.show(getFragmentManager(), null);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            Wallet wallet = application.getWallet();
            if (wallet == null) return new NoSuchPocketException("No wallet found.");
            try {
                if (wallet.isEncrypted()) {
                    KeyCrypter crypter = checkNotNull(wallet.getKeyCrypter());
                    request.aesKey = crypter.deriveKey(password);
                }
                request.signInputs = true;
                sourceAccount.completeAndSignTx(request);
                // Before broadcasting, check if there is an error, like the trade expiration
                if (error != null) throw error;
                if (!sourceAccount.broadcastTxSync(request.tx)) {
                    throw new Exception("Error broadcasting transaction: " + request.tx.getHashAsString());
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

        protected void onPostExecute(Exception error) {
            busyDialog.dismissAllowingStateLoss();
            if (mListener != null) {
                mListener.onSignResult(error);
                if (error == null && exchangeEntry != null) {
                    mListener.onTradeDeposit(exchangeEntry);
                }
            }
        }
    }
}
