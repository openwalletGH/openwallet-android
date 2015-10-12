package com.coinomi.wallet.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.SendOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author John L. Jegutanis
 */
public class TransactionAmountVisualizerAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;

    private final AbstractWallet pocket;
    private boolean isSending;
    private CoinType type;
    private String symbol;
    private List<Map.Entry<AbstractAddress, Value>> outputs;
    private boolean isInternalTransfer;
    private boolean hasFee;
    private Value feeAmount;

    private int itemCount;

    public TransactionAmountVisualizerAdapter(final Context context, final AbstractWallet walletPocket) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        pocket = walletPocket;
        type = pocket.getCoinType();
        symbol = type.getSymbol();
        outputs = new ArrayList<Map.Entry<AbstractAddress, Value>>();
    }

    public void setTransaction(AbstractTransaction tx) {
        outputs.clear();
        final Value value = tx.getValue(pocket);
        isSending = value.signum() < 0;
        // if sending and all the outputs point inside the current pocket it is an internal transfer
        isInternalTransfer = isSending;
        List<Map.Entry<AbstractAddress, Value>> outs = tx.getOutputs(pocket);
        for ( Map.Entry<AbstractAddress, Value> output : outs  ) {
            if (isSending) {
                if (pocket.getActiveAddresses().contains(output.getKey())) {
                    continue;
                }
                isInternalTransfer = false;
            } else {
                if (!pocket.getActiveAddresses().contains(output.getKey())) continue;
            }
            outputs.add(output);
        }

        feeAmount = tx.getFee(pocket);
        hasFee = feeAmount != null && !feeAmount.isZero();

        itemCount = isInternalTransfer ? 1 : outputs.size();
        itemCount += hasFee ? 1 : 0;

        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return itemCount;
    }

    @Override
    public Map.Entry<AbstractAddress, Value> getItem(int position) {
        if (position < outputs.size()) {
            return outputs.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        if (row == null) {
            row = inflater.inflate(R.layout.transaction_details_output_row, null);

            ((SendOutput) row).setSendLabel(context.getString(R.string.sent));
            ((SendOutput) row).setReceiveLabel(context.getString(R.string.received));
        }

        final SendOutput output = (SendOutput) row;
        final Map.Entry<AbstractAddress, Value> txo = getItem(position);

        if (txo == null) {
            if (position == 0) {
                output.setLabel(context.getString(R.string.internal_transfer));
                output.setSending(isSending);
                output.setAmount(null);
                output.setSymbol(null);
            } else if (hasFee) {
                output.setAmount(GenericUtils.formatCoinValue(type, feeAmount));
                output.setSymbol(symbol);
                output.setIsFee(true);
            } else { // Should not happen
                output.setLabel("???");
                output.setAmount(null);
                output.setSymbol(null);
            }
        } else {
            Value outputAmount = txo.getValue();
            output.setAmount(GenericUtils.formatCoinValue(type, outputAmount));
            output.setSymbol(symbol);
            output.setLabelAndAddress(txo.getKey());
            output.setSending(isSending);
        }

        return row;
    }
}
