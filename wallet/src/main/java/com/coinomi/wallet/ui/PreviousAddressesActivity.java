package com.coinomi.wallet.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

import com.coinomi.wallet.R;

public class PreviousAddressesActivity extends BaseWalletActivity implements
        PreviousAddressesFragment.Listener {


    private static final int LIST_ADDRESSES = 0;
    private static final int VIEW_ADDRESS = 1;

    private int currentFragment;

    private PreviousAddressesFragment addressesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        if (savedInstanceState == null) {
            addressesList = new PreviousAddressesFragment();
            addressesList.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, addressesList)
                    .commit();
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
        }

        currentFragment = LIST_ADDRESSES;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                switch (currentFragment) {
                    case LIST_ADDRESSES:
                        finish();
                        return true;
                    case VIEW_ADDRESS:
                        getSupportFragmentManager().popBackStack();
                        currentFragment = LIST_ADDRESSES;
                        return true;
                }
            default:
                // Not one of ours. Perform default menu processing
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (currentFragment == VIEW_ADDRESS) {
            getMenuInflater().inflate(R.menu.request_single_address, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onAddressSelected(Bundle args) {
        currentFragment = VIEW_ADDRESS;
        replaceFragment(AddressRequestFragment.newInstance(args), R.id.container);
    }
}
