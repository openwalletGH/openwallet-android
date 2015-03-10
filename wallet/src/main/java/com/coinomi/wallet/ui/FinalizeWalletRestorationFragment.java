package com.coinomi.wallet.ui;



import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.service.CoinService;

import org.bitcoinj.crypto.KeyCrypterScrypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that restores a wallet
 */
public class FinalizeWalletRestorationFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(FinalizeWalletRestorationFragment.class);

    private String seed;
    private String password;
    private boolean seedProtect;
    private List<CoinType> coinsToCreate;
    private boolean isTestWallet;

    private WalletFromSeedTask walletFromSeedTask;

    /**
     * Get a fragment instance.
     */
    public static Fragment newInstance(Bundle args) {
        FinalizeWalletRestorationFragment fragment = new FinalizeWalletRestorationFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public FinalizeWalletRestorationFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            Bundle args = getArguments();
            seed = args.getString(Constants.ARG_SEED);
            password = args.getString(Constants.ARG_PASSWORD);
            seedProtect = args.getBoolean(Constants.ARG_SEED_PROTECT);
            isTestWallet = args.getBoolean(Constants.ARG_TEST_WALLET, false);
            coinsToCreate = getCoinsTypes(args);

            walletFromSeedTask = new WalletFromSeedTask();
            walletFromSeedTask.execute();
        }
    }

    private List<CoinType> getCoinsTypes(Bundle args) {
        if (args.containsKey(Constants.ARG_MULTIPLE_COIN_IDS)) {
            List<CoinType> coinTypes = new ArrayList<CoinType>();
            for (String id : args.getStringArrayList(Constants.ARG_MULTIPLE_COIN_IDS)) {
                coinTypes.add(CoinID.typeFromId(id));
            }
            return coinTypes;
        } else {
            return Constants.DEFAULT_COINS;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_finalize_wallet_restoration, container, false);
    }

    WalletApplication getWalletApplication() {
        return (WalletApplication) getActivity().getApplication();
    }

    private class WalletFromSeedTask extends AsyncTask<Bundle, Void, Wallet> {
        private Dialogs.ProgressDialogFragment verifyDialog;
        private String errorMessage = "";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            verifyDialog = Dialogs.ProgressDialogFragment.newInstance(
                    getResources().getString(R.string.wallet_restoration));
            verifyDialog.show(getFragmentManager(), null);
        }

        protected Wallet doInBackground(Bundle... params) {
            ArrayList<String> seedWords = new ArrayList<String>();
            for (String word : seed.trim().split(" ")) {
                if (word.isEmpty()) continue;
                seedWords.add(word);
            }

            Wallet wallet = null;
            try {
                if (seedProtect) {
                    wallet = new Wallet(seedWords, password);
                } else {
                    wallet = new Wallet(seedWords);
                }

                KeyParameter aesKey = null;
                if (password != null && !password.isEmpty()) {
                    KeyCrypterScrypt crypter = new KeyCrypterScrypt();
                    aesKey = crypter.deriveKey(password);
                    wallet.encrypt(crypter, aesKey);
                }

                wallet.createAccounts(coinsToCreate, true, aesKey);
                getWalletApplication().setWallet(wallet);
                getWalletApplication().saveWalletNow();
                getWalletApplication().startBlockchainService(CoinService.ServiceMode.RESET_WALLET);
            } catch (Exception e) {
                log.error("Error creating a wallet", e);
                errorMessage = e.getMessage();
            }
            return wallet;
        }

        protected void onPostExecute(Wallet wallet) {
            verifyDialog.dismissAllowingStateLoss();
            if (wallet != null) {
                startWalletActivity();
            }
            else {
                showErrorAndStartIntroActivity(
                        getResources().getString(R.string.wallet_restoration_error, errorMessage));
            }
        }
    }

    private void startWalletActivity() {
        Intent intent = new Intent(getActivity(), WalletActivity.class);
        intent.putExtra(Constants.ARG_TEST_WALLET, isTestWallet);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void showErrorAndStartIntroActivity(String errorMessage) {
        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
        startActivity(new Intent(getActivity(), IntroActivity.class));
        getActivity().finish();
    }
}
