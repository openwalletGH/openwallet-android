package com.coinomi.core.wallet.families.bitcoin;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
public class TrimmedOutput extends TransactionOutput {
    final Sha256Hash txHash; // Is null if attached to a transaction
    final long index;

    public TrimmedOutput(TransactionOutput output, long index, TrimmedTransaction tx) {
        super(output.getParams(), checkNotNull(tx), output.getValue(), output.getScriptBytes());
        this.index = index;
        this.txHash = null;
    }

    public TrimmedOutput(TransactionOutput output, long index, Sha256Hash txHash) {
        super(output.getParams(), null, output.getValue(), output.getScriptBytes());
        this.index = index;
        this.txHash = txHash;
    }

    public TrimmedOutput(NetworkParameters params, long value, Sha256Hash txHash,
                         long index, byte[] scriptBytes) {
        super(params, null, Coin.valueOf(value), scriptBytes);
        this.index = index;
        this.txHash = txHash;
    }

    @Override
    public int getIndex() {
        return (int) index;
    }

    @Override
    public TrimmedOutPoint getOutPointFor() {
        return new TrimmedOutPoint(this, getTxHash());
    }

    public Sha256Hash getTxHash() {
        if (isDetached()) {
            return checkNotNull(txHash);
        } else {
            return getParentTransaction().getHash();
        }
    }

    public boolean isDetached() {
        return parent == null;
    }
}
