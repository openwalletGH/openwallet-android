package com.coinomi.wallet.ui;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.WalletApplication;

import java.util.List;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
abstract public class BaseWalletActivity extends ActionBarActivity {

    public WalletApplication getWalletApplication() {
        return (WalletApplication) getApplication();
    }

    @Nullable
    public WalletAccount getAccount(String accountId) {
        return getWalletApplication().getAccount(accountId);
    }

    public List<WalletAccount> getAllAccounts() {
        return getWalletApplication().getAllAccounts();
    }

    public List<WalletAccount> getAccounts(CoinType type) {
        return getWalletApplication().getAccounts(type);
    }

    public List<WalletAccount> getAccounts(List<CoinType> types) {
        return getWalletApplication().getAccounts(types);
    }

    public boolean isAccountExists(String accountId) {
        return getWalletApplication().isAccountExists(accountId);
    }

    public Configuration getConfiguration() {
        return getWalletApplication().getConfiguration();
    }

    public void replaceFragment(Fragment fragment, int container) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        // Replace whatever is in the fragment_container view with this fragment,
        // and add the transaction to the back stack so the user can navigate back
        transaction.replace(container, fragment);
        transaction.addToBackStack(null);

        // Commit the transaction
        transaction.commit();
    }

    @Nullable
    public Wallet getWallet() {
        return getWalletApplication().getWallet();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWalletApplication().touchLastResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        getWalletApplication().touchLastStop();
    }
}
