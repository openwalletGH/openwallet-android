package com.coinomi.wallet.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
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

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);

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
    public void onAddressSelected(Bundle args) {
        currentFragment = VIEW_ADDRESS;
        replaceFragment(AddressRequestFragment.newInstance(args));
    }
}
