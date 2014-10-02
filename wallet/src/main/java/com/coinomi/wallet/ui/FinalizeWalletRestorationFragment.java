package com.coinomi.wallet.ui;



import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.wallet.SimpleHDKeyChain;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.KeyCrypterScrypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;

import javax.annotation.Nullable;

/**
 * Fragment that restores a wallet
 */
public class FinalizeWalletRestorationFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(FinalizeWalletRestorationFragment.class);

    private String seed;
    private String password;

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
            seed = getArguments().getString(Constants.ARG_SEED);
            password = getArguments().getString(Constants.ARG_PASSWORD);
        }
        walletFromSeedTask = null;

        if (walletFromSeedTask == null || walletFromSeedTask.getStatus() == AsyncTask.Status.FINISHED) {
            walletFromSeedTask = new WalletFromSeedTask();
            walletFromSeedTask.execute(seed, password);
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

    private class WalletFromSeedTask extends AsyncTask<String, Void, Wallet> {
        private Dialogs.ProgressDialogFragment verifyDialog;
        private String errorMessage = "";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            verifyDialog = Dialogs.ProgressDialogFragment.newInstance(
                    getResources().getString(R.string.wallet_restoration));
            verifyDialog.show(getFragmentManager(), null);
        }

        protected Wallet doInBackground(String... passphraseText) {
            ArrayList<String> seed = new ArrayList<String>();
            for (String word : passphraseText[0].trim().split(" ")) {
                if (word.isEmpty()) continue;
                seed.add(word);
            }
            String password = passphraseText[1];

            Wallet wallet = null;
            try {
                wallet = new Wallet(seed, password);
                KeyCrypterScrypt crypter = new KeyCrypterScrypt();
                KeyParameter aesKey = crypter.deriveKey(password);
                wallet.encrypt(crypter, aesKey);
                wallet.createCoinPockets(Constants.DEFAULT_COINS, true, aesKey);
                getWalletApplication().setWallet(wallet);
                getWalletApplication().saveWalletNow();
            } catch (Exception e) {
                log.error("Error creating a wallet", e);
                errorMessage = e.getMessage();
            }
            return wallet;
        }

        protected void onPostExecute(Wallet wallet) {
            verifyDialog.dismiss();
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
        startActivity(new Intent(getActivity(), WalletActivity.class));
        getActivity().finish();
    }

    private void showErrorAndStartIntroActivity(String errorMessage) {
        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
        startActivity(new Intent(getActivity(), IntroActivity.class));
        getActivity().finish();
    }
}
