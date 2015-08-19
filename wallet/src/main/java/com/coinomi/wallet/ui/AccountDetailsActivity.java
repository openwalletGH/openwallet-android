package com.coinomi.wallet.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.coinomi.wallet.R;


public class AccountDetailsActivity extends BaseWalletActivity implements TradeStatusFragment.Listener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        if (savedInstanceState == null) {
            Fragment fragment = new AccountDetailsFragment();
            fragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();
        }
    }

    @Override
    public void onFinish() {
        finish();
    }
}
