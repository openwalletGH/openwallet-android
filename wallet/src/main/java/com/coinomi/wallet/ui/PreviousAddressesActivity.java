package com.coinomi.wallet.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

import com.coinomi.wallet.R;

/**
 * Activity that displays a list of previously used addresses.
 * @author John L. Jegutanis
 */
public class PreviousAddressesActivity extends BaseWalletActivity implements
        PreviousAddressesFragment.Listener {

    private static final String LIST_ADDRESSES_TAG = "list_addresses_tag";
    private static final String ADDRESS_TAG = "address_tag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        if (savedInstanceState == null) {
            PreviousAddressesFragment addressesList = new PreviousAddressesFragment();
            addressesList.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, addressesList, LIST_ADDRESSES_TAG)
                    .commit();
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getFM().findFragmentByTag(LIST_ADDRESSES_TAG).isVisible()) {
                    finish();
                    return true;
                } else {
                    getSupportFragmentManager().popBackStack();
                    return true;
                }
            default:
                // Not one of ours. Perform default menu processing
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Fragment f = getFM().findFragmentByTag(ADDRESS_TAG);
        if (f != null && f.isVisible()) {
            getMenuInflater().inflate(R.menu.request_single_address, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onAddressSelected(Bundle args) {
        replaceFragment(AddressRequestFragment.newInstance(args), R.id.container, ADDRESS_TAG);
    }
}
