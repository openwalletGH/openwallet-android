package com.coinomi.wallet.ui;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.service.CoinService;
import com.coinomi.wallet.service.CoinServiceImpl;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.crypto.KeyCrypterScrypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Fragment that restores a wallet
 */
public class FinalizeWalletRestorationFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(FinalizeWalletRestorationFragment.class);

    private static final int RESTORE_STATUS_UPDATE = 0;
    private static final int RESTORE_FINISHED = 1;

    private final Handler handler = new MyHandler(this);

    // FIXME: Ugly hack to keep a reference to the task even if the fragment is recreated
    private static WalletFromSeedTask walletFromSeedTask;
    private TextView status;


    /**
     * Get a fragment instance.
     */
    public static Fragment newInstance(Bundle args) {
        FinalizeWalletRestorationFragment fragment = new FinalizeWalletRestorationFragment();
        fragment.setRetainInstance(true);
        fragment.setArguments(args);
        return fragment;
    }

    public FinalizeWalletRestorationFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WalletApplication app = getWalletApplication();
        if (getArguments() != null) {
            Bundle args = getArguments();
            String seed = args.getString(Constants.ARG_SEED);
            String password = args.getString(Constants.ARG_PASSWORD);
            String seedPassword = args.getString(Constants.ARG_SEED_PASSWORD);
            List<CoinType> coinsToCreate = getCoinsTypes(args);

            if (walletFromSeedTask == null) {
                walletFromSeedTask = new WalletFromSeedTask(handler, app, coinsToCreate, seed, password, seedPassword);
                walletFromSeedTask.execute();
            } else {
                switch (walletFromSeedTask.getStatus()) {
                    case FINISHED:
                        handler.sendEmptyMessage(RESTORE_FINISHED);
                        break;
                    case RUNNING:
                    case PENDING:
                        walletFromSeedTask.handler = handler;
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_finalize_wallet_restoration, container, false);
        status = (TextView) view.findViewById(R.id.restoration_status);
        return view;
    }

    private List<CoinType> getCoinsTypes(Bundle args) {
        ArrayList<String> coinIds = args.getStringArrayList(Constants.ARG_MULTIPLE_COIN_IDS);
        if (coinIds != null) {
            List<CoinType> coinTypes = new ArrayList<CoinType>();
            for (String id : coinIds) {
                coinTypes.add(CoinID.typeFromId(id));
            }
            return coinTypes;
        } else {
            return Constants.DEFAULT_COINS;
        }
    }

    WalletApplication getWalletApplication() {
        return (WalletApplication) getActivity().getApplication();
    }

    static class WalletFromSeedTask extends AsyncTask<Void, String, Wallet> {
        Wallet wallet;
        String errorMessage = "";
        private final String seed;
        private final String password;
        @Nullable private final String seedPassword;
        Handler handler;
        private final WalletApplication walletApplication;
        private final List<CoinType> coinsToCreate;

        public WalletFromSeedTask(Handler handler, WalletApplication walletApplication, List<CoinType> coinsToCreate, String seed, String password, @Nullable String seedPassword) {
            this.handler = handler;
            this.walletApplication = walletApplication;
            this.coinsToCreate = coinsToCreate;
            this.seed = seed;
            this.password = password;
            this.seedPassword = seedPassword;
        }

        protected Wallet doInBackground(Void... params) {
            Intent intent = new Intent(CoinService.ACTION_CLEAR_CONNECTIONS, null,
                    walletApplication, CoinServiceImpl.class);
            walletApplication.startService(intent);

            ArrayList<String> seedWords = new ArrayList<String>();
            for (String word : seed.trim().split(" ")) {
                if (word.isEmpty()) continue;
                seedWords.add(word);
            }

            try {
                this.publishProgress("");
                walletApplication.setEmptyWallet();
                wallet = new Wallet(seedWords, seedPassword);
                KeyParameter aesKey = null;
                if (password != null && !password.isEmpty()) {
                    KeyCrypterScrypt crypter = new KeyCrypterScrypt();
                    aesKey = crypter.deriveKey(password);
                    wallet.encrypt(crypter, aesKey);
                }

                for (CoinType type : coinsToCreate) {
                    this.publishProgress(type.getName());
                    wallet.createAccount(type, false, aesKey);
                }

                walletApplication.setWallet(wallet);
                walletApplication.saveWalletNow();
            } catch (Exception e) {
                log.error("Error creating a wallet", e);
                errorMessage = e.getMessage();
            }
            return wallet;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            handler.sendMessage(handler.obtainMessage(RESTORE_STATUS_UPDATE, values[0]));
        }

        protected void onPostExecute(Wallet wallet) {
            handler.sendEmptyMessage(RESTORE_FINISHED);
        }
    }

    private static class MyHandler extends WeakHandler<FinalizeWalletRestorationFragment> {
        public MyHandler(FinalizeWalletRestorationFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(FinalizeWalletRestorationFragment ref, Message msg) {
            switch (msg.what) {
                case RESTORE_STATUS_UPDATE:
                    String workingOn = (String) msg.obj;
                    if (workingOn.isEmpty()) {
                        ref.status.setText(ref.getString(R.string.wallet_restoration_master_key));
                    } else {
                        ref.status.setText(ref.getString(R.string.wallet_restoration_coin, workingOn));
                    }
                    break;
                case RESTORE_FINISHED:
                    WalletFromSeedTask task = walletFromSeedTask;
                    walletFromSeedTask = null;
                    if (task.wallet != null) {
                        ref.startWalletActivity();
                    } else {
                        String errorMessage = ref.getResources().getString(
                                R.string.wallet_restoration_error, task.errorMessage);
                        ref.showErrorAndStartIntroActivity(errorMessage);
                    }
            }
        }
    }

    public void startWalletActivity() {
        Intent intent = new Intent(getActivity(), WalletActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        getActivity().finish();
    }

    private void showErrorAndStartIntroActivity(String errorMessage) {
        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
        startActivity(new Intent(getActivity(), IntroActivity.class));
        getActivity().finish();
    }
}
