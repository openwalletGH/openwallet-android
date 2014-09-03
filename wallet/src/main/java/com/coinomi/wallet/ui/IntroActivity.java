package com.coinomi.wallet.ui;

import android.graphics.Color;
import android.net.Uri;
import android.support.v4.app.FragmentTransaction;
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
import android.widget.ImageView;

import com.coinomi.wallet.R;
import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

public class IntroActivity extends android.support.v4.app.FragmentActivity implements WelcomeFragment.OnFragmentInteractionListener {

    public static final String RESTORE = "restore";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        if (savedInstanceState != null && savedInstanceState.getBoolean(RESTORE, false)) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new RestoreFragment())
                    .commit();
        }
        else {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new WelcomeFragment())
                    .commit();
        }
    }


    @Override
    public void onCreateNewWallet() {
        replaceFragment(new PassphraseFragment());
    }

    @Override
    public void onRestoreWallet() {
        replaceFragment(new RestoreFragment());
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
}
