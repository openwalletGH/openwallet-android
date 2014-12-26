package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.wallet.R;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import static com.coinomi.core.Preconditions.checkState;

/**
 * @author Giannis Dzegoutanis
 */
public class TransactionAmountVisualizer extends LinearLayout {


    private final SendOutput output;
    private final SendOutput fee;

    public TransactionAmountVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.transaction_amount_visualizer, this, true);

        output = (SendOutput) findViewById(R.id.transaction_output);
        output.setVisibility(View.GONE);
        fee = (SendOutput) findViewById(R.id.transaction_fee);
        fee.setVisibility(View.GONE);
    }


    public void setTransaction(WalletPocket pocket, Transaction tx) {
        CoinType type = pocket.getCoinType();
        String symbol = type.getSymbol();

        final Coin value = tx.getValue(pocket);
        final boolean isSending = value.signum() < 0;
        // if sending and all the outputs point inside the current pocket. If received
        boolean isInternalTransfer = isSending;
        output.setVisibility(View.VISIBLE);
        for (TransactionOutput txo : tx.getOutputs()) {
            if (isSending) {
                if (txo.isMine(pocket)) continue;
                isInternalTransfer = false;
            } else {
                if (!txo.isMine(pocket)) continue;
            }

            // TODO support more than one output
            output.setAmount(GenericUtils.formatCoinValue(type, txo.getValue()));
            output.setSymbol(symbol);
            output.setAddress(txo.getScriptPubKey().getToAddress(type).toString());
        }

        if (isInternalTransfer) {
            output.setLabel(getResources().getString(R.string.internal_transfer));
        }

        output.setSending(isSending);

        Coin feeAmount = tx.getFee();
        if (isSending && feeAmount != null && !feeAmount.isZero()) {
            fee.setVisibility(View.VISIBLE);
            fee.setAmount(GenericUtils.formatCoinValue(type, tx.getFee()));
            fee.setSymbol(symbol);
        }
    }
}
