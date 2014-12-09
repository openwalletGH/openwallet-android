package com.coinomi.wallet.ui;

import android.support.v7.app.ActionBarActivity;

import com.coinomi.wallet.WalletApplication;

/**
 * @author Giannis Dzegoutanis
 */
abstract public class AbstractWalletActivity extends ActionBarActivity {

    protected WalletApplication getWalletApplication() {
        return (WalletApplication) getApplication();
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
