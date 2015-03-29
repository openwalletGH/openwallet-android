package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.tasks.AddCoinTask;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.core.Address;

import java.util.ArrayList;

import javax.annotation.Nullable;


public class TradeActivity extends BaseWalletActivity implements
        TradeSelectFragment.Listener, MakeTransactionFragment.Listener,
        TradeStatusFragment.Listener, TradeSelectFragmentOld.Listener {

    private int containerRes;

    private enum State {
        INPUT, PREPARATION, SENDING, SENT, FAILED
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trade);

        containerRes = R.id.container;

//        new AlertDialog.Builder(this)
//                .setTitle(R.string.terms_of_use_title)
//                .setMessage(R.string.terms_of_use_message)
//                .setNegativeButton(R.string.button_disagree, null)
//                .setPositiveButton(R.string.button_agree, null)
//                .create().show();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(containerRes, new TradeSelectFragment())
                    .commit();
        }
    }

    @Override
    public void onMakeTrade(WalletAccount fromAccount, WalletAccount toAccount, Value amount) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_ACCOUNT_ID, fromAccount.getId());
        args.putString(Constants.ARG_SEND_TO_ACCOUNT_ID, toAccount.getId());
        if (amount.type.equals(fromAccount.getCoinType())) {
            args.putSerializable(Constants.ARG_SEND_VALUE, amount);
        } else if (amount.type.equals(toAccount.getCoinType())) {
            args.putSerializable(Constants.ARG_SEND_VALUE, amount);
        } else {
            throw new IllegalStateException("Amount does not have the expected type: " + amount.type);
        }

        replaceFragment(MakeTransactionFragment.newInstance(args), containerRes);
    }

    @Override
    public void onAbort() {
        finish();
    }

    @Override
    public void onSignResult(@Nullable Exception error) {
        if (error != null) {
            getSupportFragmentManager().popBackStack();
            DialogBuilder builder = DialogBuilder.warn(this, R.string.trade_error);
            builder.setMessage(getString(R.string.trade_error_sign_tx_message, error.getMessage()));
            builder.setPositiveButton(R.string.button_ok, null)
            .create().show();
        }
    }

    @Override
    public void onTradeDeposit(Address deposit) {
        getSupportFragmentManager().popBackStack();
        replaceFragment(TradeStatusFragment.newInstance(deposit), containerRes);
    }

    @Override
    public void onFinish() {
        finish();
    }
}
