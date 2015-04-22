package com.coinomi.wallet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeHistoryProvider;
import com.coinomi.wallet.R;

import org.bitcoinj.core.Address;

import javax.annotation.Nullable;

public class SignTransactionActivity extends AbstractWalletFragmentActivity
        implements MakeTransactionFragment.Listener {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_transaction);

        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, MakeTransactionFragment.newInstance(getIntent().getExtras()))
                .commit();
    }

    @Override
    public void onSignResult(@Nullable Exception error) {
        final Intent result = new Intent();
        result.putExtra(Constants.ARG_ERROR, error);
        setResult(RESULT_OK, result);

        // delayed finish
        new Handler().post(new Runnable()
        {
            @Override
            public void run()
            {
                finish();
            }
        });
    }

    @Override
    public void onTradeDeposit(ExchangeHistoryProvider.ExchangeEntry exchangeEntry) { }

}
