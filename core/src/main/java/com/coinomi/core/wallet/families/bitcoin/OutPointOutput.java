package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;

import static org.bitcoinj.core.TransactionInput.EMPTY_ARRAY;

/**
 * @author John L. Jegutanis
 */
public class OutPointOutput {
    final CoinType type;
    final TrimmedOutPoint outPoint;
    final TrimmedOutput output;
    final Value value;
    final boolean isGenerated;
    int appearedAtChainHeight = -1;
    int depth = 0;

    private OutPointOutput(CoinType type, TrimmedOutput output, boolean isGenerated) {
        this.type = type;
        this.output = ensureDetached(output);
        this.outPoint = output.getOutPointFor();
        this.value = type.value(output.getValue());
        this.isGenerated = isGenerated;
    }

    private TrimmedOutput ensureDetached(TrimmedOutput output) {
        if (output.isDetached()) {
            return output;
        } else {
            return new TrimmedOutput(output, output.getIndex(), output.getTxHash());
        }
    }

    public OutPointOutput(BitTransaction tx, long index) {
        this(new TrimmedOutput(tx.getOutput((int) index), index, tx.getHash()), tx.isGenerated());
    }

    public OutPointOutput(TrimmedOutput txo, boolean isGenerated) {
        this((CoinType) txo.getParams(), txo, isGenerated);
    }

    public OutPointOutput(TransactionOutPoint outPoint, TransactionOutput output,
                          boolean isGenerated) {
        this(new TrimmedOutput(output, outPoint.getIndex(), outPoint.getHash()), isGenerated);
    }

    public TrimmedOutPoint getOutPoint() {
        return outPoint;
    }

    public TransactionOutput getOutput() {
        return output;
    }

    public TransactionInput getInput() {
        return new TransactionInput(type, null, EMPTY_ARRAY, outPoint, value.toCoin());
    }

    public long getValueLong() {
        return value.value;
    }

    public Value getValue() {
        return value;
    }

    public Sha256Hash getTxHash() {
        return outPoint.getHash();
    }

    public int getAppearedAtChainHeight() {
        return appearedAtChainHeight;
    }

    public void setAppearedAtChainHeight(int appearedAtChainHeight) {
        if (appearedAtChainHeight < 0)
            throw new IllegalArgumentException("appearedAtChainHeight out of range");
        this.appearedAtChainHeight = appearedAtChainHeight;
        this.depth = 1;
    }

    public int getDepthInBlocks() {
        return depth;
    }

    public void setDepthInBlocks(int depth) {
        this.depth = depth;
    }

    public boolean isMature() {
        return !isGenerated || depth >= type.getSpendableCoinbaseDepth();
    }

    public long getIndex() {
        return outPoint.getIndex();
    }

    public byte[] getScriptBytes() {
        return output.getScriptBytes();
    }

    public Script getScriptPubKey() {
        return output.getScriptPubKey();
    }

    public boolean isGenerated() {
        return isGenerated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OutPointOutput that = (OutPointOutput) o;

        if (isGenerated != that.isGenerated) return false;
        if (!type.equals(that.type)) return false;
        if (!outPoint.equals(that.outPoint)) return false;
        return output.equals(that.output);

    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + outPoint.hashCode();
        result = 31 * result + output.hashCode();
        result = 31 * result + (isGenerated ? 1 : 0);
        return result;
    }
}
