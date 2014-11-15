package com.coinomi.wallet.ui;

import android.os.AsyncTask;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;

import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class AddCoinsActivity extends ActionBarActivity implements SelectCoinsFragment.Listener,
        PasswordConfirmationFragment.Listener{

    @CheckForNull private Wallet wallet;
    private WalletFromSeedTask addCoinTask;
    private CoinType selectedCoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_coins);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new SelectCoinsFragment())
                    .commit();
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);

        wallet = getWalletApplication().getWallet();
    }


    protected WalletApplication getWalletApplication() {
        return (WalletApplication) getApplication();
    }

    private void replaceFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Replace whatever is in the fragment_container view with this fragment,
        // and add the transaction to the back stack so the user can navigate back
        transaction.replace(R.id.container, fragment);
        transaction.addToBackStack(null);

        // Commit the transaction
        transaction.commit();
    }

    @Override
    public void onCoinSelected(CoinType type) {
        this.selectedCoin = type;
        if (wallet.isEncrypted()) {
            replaceFragment(PasswordConfirmationFragment.newInstance(
                    getResources().getString(R.string.password_add_coin, type.getName())));
        } else {
            addCoin(null);
        }
    }

    @Override
    public void onPasswordConfirmed(Bundle args) {
        addCoin(args.getString(Constants.ARG_PASSWORD));
    }


    private void addCoin(@Nullable String password) {
        if (selectedCoin != null && addCoinTask == null) {
            addCoinTask = new WalletFromSeedTask(selectedCoin, password);
            addCoinTask.execute();
        }
    }

    private class WalletFromSeedTask extends AsyncTask<Void, Void, Boolean> {
        private final CoinType type;
        @Nullable private final String password;
        private Dialogs.ProgressDialogFragment verifyDialog;
        private String errorMessage = "";

        private WalletFromSeedTask(CoinType type, @Nullable String password) {
            this.type = type;
            this.password = password;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            verifyDialog = Dialogs.ProgressDialogFragment.newInstance(
                    getResources().getString(R.string.adding_coin, type.getName()));
            verifyDialog.show(getSupportFragmentManager(), null);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            KeyParameter key = null;
            if (wallet.isEncrypted()) {
                key = wallet.getKeyCrypter().deriveKey(password);
            }
            wallet.createCoinPocket(type, true, key);
            getWalletApplication().saveWalletNow();

//            try {
//                if (seedProtect) {
//                    wallet = new Wallet(seedWords, password);
//                } else {
//                    wallet = new Wallet(seedWords);
//                }
//                KeyCrypterScrypt crypter = new KeyCrypterScrypt();
//                KeyParameter aesKey = crypter.deriveKey(password);
//                wallet.encrypt(crypter, aesKey);
//                wallet.createCoinPockets(Constants.DEFAULT_COINS, true, aesKey);
//                getWalletApplication().setWallet(wallet);
//                getWalletApplication().saveWalletNow();
//                getWalletApplication().startBlockchainService(CoinService.ServiceMode.RESET_WALLET);
//            } catch (Exception e) {
//                log.error("Error creating a wallet", e);
//                errorMessage = e.getMessage();
//            }
            return true;
        }

        protected void onPostExecute(Boolean isSuccess) {
            verifyDialog.dismiss();
            if (isSuccess) {
                Toast.makeText(AddCoinsActivity.this, errorMessage, Toast.LENGTH_LONG).show();

            } else {
                Toast.makeText(AddCoinsActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
            finish();
        }
    }
}
