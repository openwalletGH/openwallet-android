package com.coinomi.wallet.ui;

import android.os.Bundle;
import android.widget.Toast;

import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.R;

import org.bitcoinj.core.Transaction;


/**
 * @author John L. Jegutanis
 */
public class SendActivity extends BaseWalletActivity implements SendFragment.Listener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fragment_wrapper);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, SendFragment.newInstance(getIntent().getData()))
                    .commit();
        }
    }

    @Override
    public void onTransactionBroadcastSuccess(WalletAccount pocket, Transaction transaction) {
        Toast.makeText(this, getString(R.string.sent_msg), Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onTransactionBroadcastFailure(WalletAccount pocket, Transaction transaction) {
        Toast.makeText(this, getString(R.string.get_tx_broadcast_error), Toast.LENGTH_LONG).show();
        finish();
    }
}
