package com.coinomi.wallet.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;

import com.coinomi.wallet.WalletApplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
public class AbstractWalletActivity extends ActionBarActivity {

    private WalletApplication application;

    protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        application = (WalletApplication) getApplication();

        super.onCreate(savedInstanceState);
    }

    protected WalletApplication getWalletApplication()
    {
        return application;
    }
}
