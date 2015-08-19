package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.Constants;
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
        replaceFragment(new SeedFragment());
    }

    @Override
    public void onRestoreWallet() {
        replaceFragment(RestoreFragment.newInstance());
    }

    @Override
    public void onTestWallet() {
        if (getWalletApplication().getWallet() == null) {
            makeTestWallet();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.test_wallet_warning_title)
                    .setMessage(R.string.test_wallet_warning_message)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_confirm, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            makeTestWallet();
                        }
                    })
                    .create().show();
        }

    }

    private void makeTestWallet() {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_SEED, Wallet.generateMnemonicString(Constants.SEED_ENTROPY_DEFAULT));
        args.putString(Constants.ARG_PASSWORD, null);
        args.putStringArrayList(Constants.ARG_MULTIPLE_COIN_IDS, Constants.DEFAULT_TEST_COIN_IDS);
        args.putBoolean(Constants.ARG_TEST_WALLET, true);

        replaceFragment(FinalizeWalletRestorationFragment.newInstance(args));
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
