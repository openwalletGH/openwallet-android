package com.coinomi.wallet.ui;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;

import com.coinomi.wallet.R;

public class TransactionDetailsActivity extends AbstractWalletActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);
        if (savedInstanceState == null) {
            TransactionDetailsFragment fragment = new TransactionDetailsFragment();
            fragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();

        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);
    }
}
