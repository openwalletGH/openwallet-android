package com.coinomi.wallet.ui;

import android.content.DialogInterface;
import android.os.Bundle;

import com.coinomi.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class ShowSeedActivity  extends BaseWalletActivity implements ShowSeedFragment.Listener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new ShowSeedFragment())
                    .commit();
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);
    }

    @Override
    public void onSeedNotAvailable() {
        DialogBuilder.warn(this, R.string.seed_not_available_title)
                .setMessage(R.string.seed_not_available)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .create().show();
    }
}
