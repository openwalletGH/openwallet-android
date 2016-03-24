package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.network.ConnectivityHelper;
import com.coinomi.core.network.ServerClients;
import com.coinomi.core.wallet.BitWalletSingleKey;
import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.SerializedKey;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.families.bitcoin.BitTransaction;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.Keyboard;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnFocusChange;
import butterknife.OnTextChanged;

import static butterknife.OnTextChanged.Callback.AFTER_TEXT_CHANGED;
import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.wallet.util.UiUtils.setGone;
import static com.coinomi.wallet.util.UiUtils.setVisible;


/**
 * A simple {@link Fragment} subclass.
 *
 */
public class SweepWalletFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SweepWalletFragment.class);

    private static final int REQUEST_CODE_SCAN = 0;

    private static final String ERROR = "error";
    private static final String STATUS = "status";

    enum Error {NONE, BAD_FORMAT, BAD_COIN_TYPE, BAD_PASSWORD, ZERO_COINS, NO_CONNECTION, GENERIC_ERROR}
    enum TxStatus {INITIAL, DECODING, LOADING, SIGNING}

    // FIXME: Improve this: a reference to the task even if the fragment is recreated
    static SweepWalletTask sweepWalletTask;

    private final Handler handler = new MyHandler(this);
    private Listener listener;
    private ServerClients serverClients;
    private WalletAccount account;
    private SerializedKey serializedKey;
    private Error error = Error.NONE;
    private TxStatus status = TxStatus.INITIAL;

    @Bind(R.id.private_key_input) View privateKeyInputView;
    @Bind(R.id.sweep_wallet_key) EditText privateKeyText;
    @Bind(R.id.passwordView) View passwordView;
    @Bind(R.id.sweep_error) TextView errorΜessage;
    @Bind(R.id.passwordInput) EditText password;
    @Bind(R.id.sweep_loading) View sweepLoadingView;
    @Bind(R.id.sweeping_status) TextView sweepStatus;
    @Bind(R.id.button_next) Button nextButton;

    public SweepWalletFragment() { }

    public static SweepWalletFragment newInstance() {
        clearTasks();
        return new SweepWalletFragment();
    }

    static void clearTasks() {
        if (sweepWalletTask != null) {
            sweepWalletTask.cancel(true);
            sweepWalletTask = null;
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        checkNotNull(getArguments(), "Must provide arguments with an account id.");

        WalletApplication application = (WalletApplication) getActivity().getApplication();
        account = application.getAccount(getArguments().getString(Constants.ARG_ACCOUNT_ID));
        if (account == null) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
            getActivity().finish();
        }

        if (savedState != null) {
            error = (Error) savedState.getSerializable(ERROR);
            status = (TxStatus) savedState.getSerializable(STATUS);
        }

        if (sweepWalletTask != null) {
            switch (sweepWalletTask.getStatus()) {
                case FINISHED:
                    sweepWalletTask.onPostExecute(null);
                    break;
                case RUNNING:
                case PENDING:
                    sweepWalletTask.handler = handler;
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sweep, container, false);
        ButterKnife.bind(this, view);

        if (getArguments().containsKey(Constants.ARG_PRIVATE_KEY)) {
            privateKeyText.setText(getArguments().getString(Constants.ARG_PRIVATE_KEY));
        }

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATUS, status);
        outState.putSerializable(ERROR, error);
    }

    @Override
    public void onResume() {
        super.onResume();
        validatePrivateKey();
        updateView();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            listener = (Listener) context;
            // TODO implement differently
            ConnectivityHelper connHelper = new ConnectivityHelper() {
                ConnectivityManager connManager = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);

                @Override
                public boolean isConnected() {
                    NetworkInfo activeInfo = connManager.getActiveNetworkInfo();
                    return activeInfo != null && activeInfo.isConnected();
                }
            };

            serverClients = new ServerClients(Constants.DEFAULT_COINS_SERVERS, connHelper);
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " + Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @OnClick(R.id.scan_qr_code)
    void handleScan() {
        startActivityForResult(new Intent(getActivity(), ScanActivity.class), REQUEST_CODE_SCAN);
    }

    @OnClick(R.id.button_next)
    void verifyKeyAndProceed() {
        Keyboard.hideKeyboard(getActivity());
        if (validatePrivateKey()) {
            maybeStartSweepTask();
        }
    }

    @OnFocusChange(R.id.sweep_wallet_key)
    void onPrivateKeyInputFocusChange(final boolean hasFocus) {
        if (!hasFocus) validatePrivateKey();
    }

    @OnTextChanged(value = R.id.sweep_wallet_key, callback = AFTER_TEXT_CHANGED)
    void onPrivateKeyInputTextChange() {
        validatePrivateKey(true);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                privateKeyText.setText(intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT));
                validatePrivateKey();
            }
        }
    }

    private void updateView() {
        updateErrorView();
        updateStatusView();
        nextButton.setEnabled(status == TxStatus.INITIAL);
    }

    private void updateErrorView() {
        switch (error) {
            case NONE:
                setGone(errorΜessage);
                break;
            case BAD_FORMAT:
                errorΜessage.setText(R.string.sweep_wallet_bad_format);
                setVisible(errorΜessage);
                break;
            case BAD_COIN_TYPE:
                errorΜessage.setText(R.string.sweep_wallet_bad_coin_type);
                setVisible(errorΜessage);
                break;
            case BAD_PASSWORD:
                errorΜessage.setText(R.string.sweep_wallet_bad_password);
                setVisible(errorΜessage);
                break;
            case ZERO_COINS:
                errorΜessage.setText(R.string.sweep_wallet_zero_coins);
                setVisible(errorΜessage);
                break;
            case NO_CONNECTION:
                errorΜessage.setText(R.string.disconnected_label);
                setVisible(errorΜessage);
                break;
            case GENERIC_ERROR:
                errorΜessage.setText(R.string.error_generic);
                setVisible(errorΜessage);
                break;
        }
    }

    private void updateStatusView() {
        // Hide when error
        if (error != Error.NONE) {
            setGone(sweepLoadingView);
            setVisible(privateKeyInputView);
            return;
        }

        switch (status) {
            case DECODING:
                sweepStatus.setText(R.string.sweep_wallet_key_decoding);
                break;
            case LOADING:
                sweepStatus.setText(R.string.sweep_wallet_key_loading);
                break;
            case SIGNING:
                sweepStatus.setText(R.string.sweep_wallet_key_signing);
                break;
        }

        if (status == TxStatus.INITIAL) {
            setGone(sweepLoadingView);
            setVisible(privateKeyInputView);

            if (serializedKey != null && serializedKey.isEncrypted()) {
                passwordView.setVisibility(View.VISIBLE);
            } else {
                passwordView.setVisibility(View.GONE);
                password.setText("");
            }
        } else {
            setVisible(sweepLoadingView);
            setGone(privateKeyInputView);
        }
    }
    private void onTransactionPrepared(SendRequest request) {
        sweepWalletTask = null;
        error = Error.NONE;
        status = TxStatus.INITIAL;

        if (listener != null) {
            listener.onSendTransaction(checkNotNull(request));
        }
    }

    private void onStatusUpdate(TxStatus status) {
        this.status = status;
        updateStatusView();
    }

    private void onError(Error error) {
        sweepWalletTask = null;
        this.error = error;
        status = TxStatus.INITIAL;
        updateView();
    }

    private void maybeStartSweepTask() {
        if (sweepWalletTask == null) {
            sweepWalletTask = new SweepWalletTask(handler, serverClients, account, serializedKey,
                    password.getText().toString());
            sweepWalletTask.execute();
            error = Error.NONE;
            status = TxStatus.DECODING;
            updateView();
        }
    }

    private boolean validatePrivateKey() {
        return validatePrivateKey(false);
    }

    private boolean validatePrivateKey(boolean isTyping) {
        if (privateKeyText == null) return false;

        String privateKey = privateKeyText.getText().toString().trim();

        if (privateKey.isEmpty()) return false;

        try {
            serializedKey = new SerializedKey(privateKey);
            error = Error.NONE;
        } catch (SerializedKey.KeyFormatException e) {
            serializedKey = null;
            if (isTyping) {
                error = Error.NONE;
            } else {
                log.info("Invalid private key: {}", e.getMessage());
                error = Error.BAD_FORMAT;
            }
        }

        updateView();

        return serializedKey != null;
    }

    static class SweepWalletTask extends AsyncTask<Void, TxStatus, Void> {
        Handler handler;
        Error error = Error.NONE;
        SendRequest request = null;
        final WalletAccount sendToAccount;
        final CoinType type;
        final ServerClients serverClients;
        final SerializedKey key;
        @Nullable final String keyPassword;

        public SweepWalletTask(Handler handler, ServerClients serverClients,
                               WalletAccount sendToAccount, SerializedKey key,
                               @Nullable String keyPassword) {
            this.handler = handler;
            this.serverClients = serverClients;
            this.key = key;
            this.sendToAccount = sendToAccount;
            this.type = sendToAccount.getCoinType();
            this.keyPassword = keyPassword;
        }

        protected Void doInBackground(Void... params) {
            startSweeping();
            serverClients.stopAllAsync();
            return null;
        }

        private void startSweeping() {
            log.info("Starting sweep wallet task. Decoding private key...");
            this.publishProgress(TxStatus.DECODING);
            SerializedKey.TypedKey rawKey;
            try {
                if (key.isEncrypted()) {
                    rawKey = key.getKey(keyPassword);
                } else {
                    rawKey = key.getKey();
                }
            } catch (SerializedKey.BadPassphraseException e) {
                log.info("Could not get key due to bad passphrase");
                error = Error.BAD_PASSWORD;
                return;
            }

            if (!rawKey.possibleType.contains(type)) {
                log.info("Incorrect coin type");
                error = Error.BAD_COIN_TYPE;
                return;
            }

            log.info("Creating temporary wallet");
            this.publishProgress(TxStatus.LOADING);
            BitWalletSingleKey sweepWallet = new BitWalletSingleKey(type, rawKey.key);
            serverClients.startAsync(sweepWallet);

            int maxWaitMs = Constants.NETWORK_TIMEOUT_MS;
            log.info("Waiting wallet to connect...");
            while(!sweepWallet.isConnected() && maxWaitMs > 0) {
                try {
                    Thread.sleep(100);
                    maxWaitMs -= 100;
                } catch (InterruptedException e) {
                    log.info("Stopping wallet loading task...");
                    return;
                }
            }
            if (!sweepWallet.isConnected()) {
                error = Error.NO_CONNECTION;
                return;
            }

            log.info("Waiting wallet to load...");
            while(sweepWallet.isLoading()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    log.info("Stopping wallet loading task...");
                    return;
                }
            }

            Value balance = sweepWallet.getBalance(true);
            if (balance.isPositive()) {
                log.info("Wallet balance is {}", balance);
                this.publishProgress(TxStatus.SIGNING);
                try {
                    request = sweepWallet
                            .getEmptyWalletRequest(sendToAccount.getReceiveAddress());
                    request.useUnsafeOutputs = true;
                    sweepWallet.completeAndSignTx(request);
                    // Connect inputs so we can show the fees in the next screen
                    BitTransaction tx = (BitTransaction) request.tx;
                    for (TransactionInput txi : tx.getInputs()) {
                        TransactionOutPoint outPoint = txi.getOutpoint();
                        BitTransaction connectedTx =
                                checkNotNull(sweepWallet.getTransaction(outPoint.getHash()));
                        txi.connect(connectedTx.getOutput((int) outPoint.getIndex()));
                    }
                } catch (WalletAccount.WalletAccountException e) {
                    log.info("Could not create transaction: {}", e.getMessage());
                    error = Error.GENERIC_ERROR;
                }
            } else {
                log.info("Wallet is empty");
                error = Error.ZERO_COINS;
            }
        }

        @Override
        protected void onProgressUpdate(TxStatus... values) {
            handler.sendMessage(handler.obtainMessage(MyHandler.TX_STATUS_UPDATE, values[0]));
        }

        protected void onPostExecute(Void param) {
            if (request != null) {
                handler.sendMessage(
                        handler.obtainMessage(MyHandler.TX_PREPARATION_FINISHED, request));
            } else if (error != Error.NONE) {
                handler.sendMessage(handler.obtainMessage(MyHandler.TX_PREPARATION_ERROR, error));
            }
        }
    }

    private static class MyHandler extends WeakHandler<SweepWalletFragment> {
        static final int TX_STATUS_UPDATE = 0;
        static final int TX_PREPARATION_FINISHED = 1;
        static final int TX_PREPARATION_ERROR = 2;

        public MyHandler(SweepWalletFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(SweepWalletFragment ref, Message msg) {
            switch (msg.what) {
                case TX_STATUS_UPDATE:
                    ref.onStatusUpdate((TxStatus) msg.obj);
                    break;
                case TX_PREPARATION_FINISHED:
                    ref.onTransactionPrepared((SendRequest) msg.obj);
                    break;
                case TX_PREPARATION_ERROR:
                    ref.onError((Error) msg.obj);
                    break;
            }
        }
    }

    public interface Listener {
        void onSendTransaction(SendRequest request);
    }

}
