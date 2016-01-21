package com.coinomi.core.wallet.families.bitcoin;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

/**
 * @author John L. Jegutanis
 */
public class TrimmedOutPoint extends TransactionOutPoint {
    final TrimmedOutput connectedOutput;

    public TrimmedOutPoint(TrimmedOutput txo, Sha256Hash txHash) {
        super(txo.getParams(), txo.getIndex(), txHash);
        connectedOutput = txo;
    }

    public TrimmedOutPoint(NetworkParameters params, long index, Sha256Hash hash) {
        super(params, index, hash);
        this.connectedOutput = null;
    }

    public static TrimmedOutPoint get(TransactionOutPoint outPoint) {
        return new TrimmedOutPoint(outPoint.getParams(), outPoint.getIndex(), outPoint.getHash());
    }

    public static TrimmedOutPoint get(TransactionOutput txo) {
        return get(txo.getOutPointFor());
    }

    public static TrimmedOutPoint get(TransactionInput input) {
        return get(input.getOutpoint());
    }

    @Override
    public TransactionOutput getConnectedOutput() {
        return connectedOutput;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrimmedOutPoint other = (TrimmedOutPoint) o;
        return getIndex() == other.getIndex() &&
                getHash().equals(other.getHash());
    }
}
