package com.coinomi.wallet.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeHistoryProvider.ExchangeEntry;
import com.coinomi.wallet.R;
import com.coinomi.wallet.tasks.AddCoinTask;
import com.coinomi.wallet.ui.dialogs.ConfirmAddCoinUnlockWalletDialog;

import org.bitcoinj.crypto.KeyCrypterException;

import javax.annotation.Nullable;


public class TradeActivity extends BaseWalletActivity implements
        TradeSelectFragment.Listener, MakeTransactionFragment.Listener, TradeStatusFragment.Listener,
        ConfirmAddCoinUnlockWalletDialog.Listener {

    private static final String TRADE_SELECT_FRAGMENT_TAG = "trade_select_fragment_tag";

    private int containerRes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_wrapper);

        containerRes = R.id.container;

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(containerRes, new TradeSelectFragment(), TRADE_SELECT_FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    public void onMakeTrade(WalletAccount fromAccount, WalletAccount toAccount, Value amount) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_ACCOUNT_ID, fromAccount.getId());
        args.putString(Constants.ARG_SEND_TO_ACCOUNT_ID, toAccount.getId());
        if (amount.type.equals(fromAccount.getCoinType())) {
            // TODO set the empty wallet flag in the fragment
            // Decide if emptying wallet or not
            Value lastBalance = fromAccount.getBalance();
            if (amount.compareTo(lastBalance) == 0) {
                args.putSerializable(Constants.ARG_EMPTY_WALLET, true);
            } else {
                args.putSerializable(Constants.ARG_SEND_VALUE, amount);
            }
        } else if (amount.type.equals(toAccount.getCoinType())) {
            args.putSerializable(Constants.ARG_SEND_VALUE, amount);
        } else {
            throw new IllegalStateException("Amount does not have the expected type: " + amount.type);
        }

        replaceFragment(MakeTransactionFragment.newInstance(args), containerRes);
    }

    @Override
    public void onSignResult(@Nullable Exception error, ExchangeEntry exchangeEntry) {
        if (error != null) {
            getSupportFragmentManager().popBackStack();
            // Ignore wallet decryption errors
            if (!(error instanceof KeyCrypterException)) {
                DialogBuilder builder = DialogBuilder.warn(this, R.string.trade_error);
                builder.setMessage(getString(R.string.trade_error_sign_tx_message, error.getMessage()));
                builder.setPositiveButton(R.string.button_ok, null)
                        .create().show();
            }
        } else if (exchangeEntry != null) {
            getSupportFragmentManager().popBackStack();
            replaceFragment(TradeStatusFragment.newInstance(exchangeEntry, true), containerRes);
        }
    }

    @Override
    public void onFinish() {
        finish();
    }

    @Override
    public void addCoin(CoinType type, String description, CharSequence password) {
        Fragment f = getFM().findFragmentByTag(TRADE_SELECT_FRAGMENT_TAG);
        if (f != null && f.isVisible() && f instanceof TradeSelectFragment) {
            ((TradeSelectFragment) f).maybeStartAddCoinAndProceedTask(description, password);
        }
    }
}
