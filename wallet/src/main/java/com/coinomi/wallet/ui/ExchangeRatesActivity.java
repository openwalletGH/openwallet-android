package com.coinomi.wallet.ui;

import android.os.Bundle;

import com.coinomi.wallet.R;

/**
 * @author Giannis Dzegoutanis
 */
public class ExchangeRatesActivity extends AbstractWalletActionBarActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exchange_rates_content);

        if (savedInstanceState == null) {
            ExchangeRatesFragment fragment = new ExchangeRatesFragment();
            fragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);
    }
}
