package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import com.coinomi.wallet.R;

public class IntroActivity extends AbstractWalletFragmentActivity
        implements WelcomeFragment.Listener, PasswordConfirmationFragment.Listener,
        SetPasswordFragment.Listener, SelectCoinsFragment.Listener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        // If we detected that this device is incompatible
        if (!getWalletApplication().getConfiguration().isDeviceCompatible()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.incompatible_device_warning_title)
                    .setMessage(R.string.incompatible_device_warning_message)
                    .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .create().show();
        } else {
            if (savedInstanceState == null) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.container, new WelcomeFragment())
                        .commit();
            }
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
    public void onCreateNewWallet() {
        if (getWalletApplication().getWallet() == null) {
            replaceFragment(new SeedFragment());
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.override_wallet_warning_title)
                    .setMessage(R.string.override_new_wallet_warning_message)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            replaceFragment(new SeedFragment());
                        }
                    })
                    .create().show();
        }
    }

    @Override
    public void onRestoreWallet() {
        if (getWalletApplication().getWallet() == null) {
            replaceFragment(RestoreFragment.newInstance());
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.override_wallet_warning_title)
                    .setMessage(R.string.override_restore_wallet_warning_message)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            replaceFragment(RestoreFragment.newInstance());
                        }
                    })
                    .create().show();
        }
    }

    @Override
    public void onSeedCreated(String seed) {
        replaceFragment(RestoreFragment.newInstance(seed));
    }

    @Override
    public void onSeedVerified(Bundle args) {
        replaceFragment(SetPasswordFragment.newInstance(args));
    }

    @Override
    public void onPasswordConfirmed(Bundle args) {
        selectCoins(args);
    }

    @Override
    public void onPasswordSet(Bundle args) {
        selectCoins(args);
    }

    private void selectCoins(Bundle args) {
        String message = getResources().getString(R.string.select_coins);
        replaceFragment(SelectCoinsFragment.newInstance(message, true, args));
    }

    @Override
    public void onCoinSelection(Bundle args) {
        replaceFragment(FinalizeWalletRestorationFragment.newInstance(args));
    }
}
