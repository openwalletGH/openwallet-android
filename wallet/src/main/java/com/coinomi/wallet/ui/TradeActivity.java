package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;


public class TradeActivity extends BaseWalletActivity {

    private int containerRes;


    private enum State {
        INPUT, PREPARATION, SENDING, SENT, FAILED
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trade);

        containerRes = R.id.container;

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(containerRes, new TradeSelectFragment())
                    .commit();
        }
    }

//    @Override
//    public void onCreateNewWallet() {
//        replaceFragment(new SeedFragment(), containerRes);
//    }
//
//    @Override
//    public void onRestoreWallet() {
//        replaceFragment(RestoreFragment.newInstance(), containerRes);
//    }
//
//    @Override
//    public void onTestWallet() {
//        if (getWalletApplication().getWallet() == null) {
//            makeTestWallet();
//        } else {
//            new AlertDialog.Builder(this)
//                    .setTitle(R.string.test_wallet_warning_title)
//                    .setMessage(R.string.test_wallet_warning_message)
//                    .setNegativeButton(R.string.button_cancel, null)
//                    .setPositiveButton(R.string.button_confirm, new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            makeTestWallet();
//                        }
//                    })
//                    .create().show();
//        }
//
//    }
//
//
//    @Override
//    public void onSeedCreated(String seed) {
//        replaceFragment(RestoreFragment.newInstance(seed));
//    }
//
//    @Override
//    public void onNewSeedVerified(String seed) {
//        replaceFragment(SetPasswordFragment.newInstance(seed));
//    }
//
//    @Override
//    public void onExistingSeedVerified(String seed, boolean isSeedProtected) {
//        Bundle args = new Bundle();
//        args.putString(Constants.ARG_SEED, seed);
//        args.putBoolean(Constants.ARG_SEED_PROTECT, isSeedProtected);
//        if (isSeedProtected) {
//            replaceFragment(PasswordConfirmationFragment.newInstance(
//                    getResources().getString(R.string.password_wallet_recovery), args));
//        } else {
//            replaceFragment(PasswordConfirmationFragment.newInstance(
//                    getResources().getString(R.string.set_password_info), args));
//        }
//    }
//
//    @Override
//    public void onPasswordConfirmed(Bundle args) {
//        selectCoins(args);
//    }
//
//    @Override
//    public void onPasswordSet(Bundle args) {
//        selectCoins(args);
//    }
//
//    private void selectCoins(Bundle args) {
//        String message = getResources().getString(R.string.select_coins);
//        replaceFragment(SelectCoinsFragment.newInstance(message, true, args));
//    }
//
//    @Override
//    public void onCoinSelection(Bundle args) {
//        replaceFragment(FinalizeWalletRestorationFragment.newInstance(args));
//    }
}
