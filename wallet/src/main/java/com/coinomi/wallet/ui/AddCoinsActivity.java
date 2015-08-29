package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.tasks.AddCoinTask;

import java.util.ArrayList;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class AddCoinsActivity extends BaseWalletActivity
        implements SelectCoinsFragment.Listener {

    @CheckForNull private Wallet wallet;
    private MyAddCoinTask addCoinTask;
    private CoinType selectedCoin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new SelectCoinsFragment())
                    .commit();
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);

        wallet = getWalletApplication().getWallet();
    }

    @Override
    public void onCoinSelection(Bundle args) {
        ArrayList<String> ids = args.getStringArrayList(Constants.ARG_MULTIPLE_COIN_IDS);

        // For new we add only one coin at a time
        selectedCoin = CoinID.typeFromId(ids.get(0));

        if (wallet.isAccountExists(selectedCoin)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.coin_already_added_title, selectedCoin.getName()))
                    .setMessage(R.string.coin_already_added)
                    .setPositiveButton(R.string.button_ok, null)
                    .create().show();
            return;
        }

        if (wallet.isEncrypted()) {
            addCoinPasswordDialog.show(getSupportFragmentManager(), null);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.adding_coin_confirmation_title, selectedCoin.getName()))
                    .setPositiveButton(R.string.button_add, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            addCoin(null);
                        }
                    })
                    .setNegativeButton(R.string.button_cancel, null)
                    .create().show();
        }
    }

    private void addCoin(@Nullable CharSequence password) {
        if (selectedCoin != null && addCoinTask == null) {
            addCoinTask = new MyAddCoinTask(selectedCoin, wallet, password);
            addCoinTask.execute();
        }
    }

    private class MyAddCoinTask extends AddCoinTask {
        private Dialogs.ProgressDialogFragment verifyDialog;

        public MyAddCoinTask(CoinType type, Wallet wallet, @Nullable CharSequence password) {
            super(type, wallet, password);
        }

        @Override
        protected void onPreExecute() {
            verifyDialog = Dialogs.ProgressDialogFragment.newInstance(
                    getResources().getString(R.string.adding_coin_working, type.getName()));
            verifyDialog.show(getSupportFragmentManager(), null);
        }

        @Override
        protected void onPostExecute(Exception e, WalletAccount newAccount) {
            verifyDialog.dismiss();
            result(newAccount, e == null ? null : e.getMessage());
        }
    }

    public void result(WalletAccount newAccount, @Nullable String errorMessage) {
        final Intent result = new Intent();
        if (errorMessage != null) {
            String message = getResources().getString(R.string.add_coin_error,
                    selectedCoin.getName(), errorMessage);
            Toast.makeText(AddCoinsActivity.this, message, Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED, result);
        } else {
            result.putExtra(Constants.ARG_ACCOUNT_ID, newAccount.getId());
            setResult(RESULT_OK, result);
        }

        finish();
    }

    private DialogFragment addCoinPasswordDialog = new DialogFragment() {
        public TextView passwordView;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final LayoutInflater inflater = LayoutInflater.from(getActivity());
            final View view = inflater.inflate(R.layout.get_password_dialog, null);
            passwordView = (TextView) view.findViewById(R.id.password);

            return new DialogBuilder(getActivity())
                    .setTitle(getString(R.string.adding_coin_confirmation_title, selectedCoin.getName()))
                    .setView(view)
                    .setNegativeButton(R.string.button_cancel, null)
                    .setPositiveButton(R.string.button_add, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            addCoin(passwordView.getText());
                        }
                    }).create();
        }
    };
}
