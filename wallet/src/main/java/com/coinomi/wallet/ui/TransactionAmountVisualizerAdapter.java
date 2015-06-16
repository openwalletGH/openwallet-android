package com.coinomi.wallet.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.SendOutput;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.util.ArrayList;
import java.util.List;

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
    private List<TransactionOutput> outputs;
    private boolean isInternalTransfer;
    private boolean hasFee;
    private Coin feeAmount;

    private int itemCount;

    public TransactionAmountVisualizerAdapter(final Context context, final AbstractWallet walletPocket) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        pocket = walletPocket;
        type = pocket.getCoinType();
        symbol = type.getSymbol();
        outputs = new ArrayList<TransactionOutput>();
    }

    public void setTransaction(Transaction tx) {
        outputs.clear();
        final Coin value = tx.getValue(pocket);
        isSending = value.signum() < 0;
        // if sending and all the outputs point inside the current pocket it is an internal transfer
        isInternalTransfer = isSending;
        for (TransactionOutput txo : tx.getOutputs()) {
            if (isSending) {
                if (txo.isMine(pocket)) continue;
                isInternalTransfer = false;
            } else {
                if (!txo.isMine(pocket)) continue;
            }
            outputs.add(txo);
        }

        feeAmount = tx.getFee();
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
    public TransactionOutput getItem(int position) {
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
        final TransactionOutput txo = getItem(position);

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
            Coin outputAmount = txo.getValue();
            output.setAmount(GenericUtils.formatCoinValue(type, outputAmount));
            output.setSymbol(symbol);
            String address = txo.getScriptPubKey().getToAddress(type).toString();
            output.setLabelAndAddress(
                    AddressBookProvider.resolveLabel(context, type, address), address);
            output.setSending(isSending);
        }

        return row;
    }
}
