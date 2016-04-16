package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.tasks.AddCoinTask;
import com.coinomi.wallet.ui.dialogs.ConfirmAddCoinUnlockWalletDialog;

import org.bitcoinj.crypto.KeyCrypterException;

import java.util.ArrayList;

import javax.annotation.CheckForNull;

public class AddCoinsActivity extends BaseWalletActivity
        implements SelectCoinsFragment.Listener, AddCoinTask.Listener,
        ConfirmAddCoinUnlockWalletDialog.Listener {

    private static final String ADD_COIN_TASK_BUSY_DIALOG_TAG = "add_coin_task_busy_dialog_tag";
    private static final String ADD_COIN_DIALOG_TAG = "ADD_COIN_DIALOG_TAG";

    @CheckForNull private Wallet wallet;
    private AddCoinTask addCoinTask;
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

        showAddCoinDialog();
    }

    private void showAddCoinDialog() {
        Dialogs.dismissAllowingStateLoss(getFM(), ADD_COIN_DIALOG_TAG);
        ConfirmAddCoinUnlockWalletDialog.getInstance(selectedCoin, wallet.isEncrypted())
                .show(getFM(), ADD_COIN_DIALOG_TAG);
    }

    @Override
    public void addCoin(CoinType type, String description, CharSequence password) {
        if (type != null && addCoinTask == null) {
            addCoinTask = new AddCoinTask(this, type, wallet, description, password);
            addCoinTask.execute();
        }
    }

    @Override
    public void onAddCoinTaskStarted() {
        Dialogs.ProgressDialogFragment.show(getSupportFragmentManager(),
                getString(R.string.adding_coin_working, selectedCoin.getName()),
                ADD_COIN_TASK_BUSY_DIALOG_TAG);
    }

    @Override
    public void onAddCoinTaskFinished(Exception error, WalletAccount newAccount) {
        if (Dialogs.dismissAllowingStateLoss(getSupportFragmentManager(), ADD_COIN_TASK_BUSY_DIALOG_TAG)) return;
        addCoinTask = null;
        final Intent result = new Intent();
        if (error != null) {
            if (error instanceof KeyCrypterException) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.unlocking_wallet_error_title))
                        .setMessage(R.string.unlocking_wallet_error_detail)
                        .setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                showAddCoinDialog();
                            }
                        })
                        .setNegativeButton(R.string.button_cancel, null)
                        .create().show();
            } else {
                String message = getResources().getString(R.string.add_coin_error,
                        selectedCoin.getName(), error.getMessage());
                Toast.makeText(AddCoinsActivity.this, message, Toast.LENGTH_LONG).show();
                setResult(RESULT_CANCELED, result);
                finish();
            }
        } else {
            result.putExtra(Constants.ARG_ACCOUNT_ID, newAccount.getId());
            setResult(RESULT_OK, result);
            finish();
        }

    }
}
