package com.coinomi.core.wallet.families.bitcoin;

import com.google.common.collect.ImmutableList;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkArgument;
import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class TrimmedTransaction extends Transaction {
    private final Sha256Hash hash;
    // Holds the non trimmed outputs, will be null if all original outputs are added
    @Nullable private HashMap<Integer, TransactionOutput> trimmedOutputs;
    private final int numberOfOutputs;

    public TrimmedTransaction(NetworkParameters params, Sha256Hash hash, int numberOfOutputs) {
        super(params);
        checkState(numberOfOutputs >= 0, "Number of outputs cannot be negative");
        this.hash = hash;
        trimmedOutputs = new HashMap<>();
        this.numberOfOutputs = numberOfOutputs;
    }

    @Override
    public Sha256Hash getHash() {
        return hash;
    }

    public TransactionOutput addOutput(int index, TransactionOutput to) {
        checkArgument(index >= 0 && index < numberOfOutputs, "Index out of range");
        checkNotNull(trimmedOutputs, "Cannot add more outputs")
                .put(index, new TrimmedOutput(to, index, this));
        return to;
    }

    public void addAllOutputs(List<TransactionOutput> outputs) {
        checkArgument(outputs.size() == numberOfOutputs, "Number of outputs don't match");
        if (trimmedOutputs != null) {
            trimmedOutputs = null;
        }
        long index = 0;
        for (TransactionOutput output : outputs) {
            super.addOutput(new TrimmedOutput(output, index++, this));
        }
    }

    @Override
    public TransactionOutput addOutput(TransactionOutput to) {
        throw new IllegalArgumentException(
                "Use addOutput(int, TransactionOutput) or addAllOutputs(List<TransactionOutput>)");
    }

    public boolean isOutputAvailable(int index) {
        checkIndex(index);

        if (trimmedOutputs == null) {
            return index < super.getOutputs().size();
        } else {
            return trimmedOutputs.containsKey(index);
        }
    }

    public int getNumberOfOutputs() {
        return numberOfOutputs;
    }

    @Override
    public TransactionOutput getOutput(int index) {
        checkIndex(index);

        if (trimmedOutputs == null) {
            return super.getOutput(index);
        } else {
            TransactionOutput output = trimmedOutputs.get(index);
            // Trimmed outputs are empty
            if (output == null) {
                return EmptyTransactionOutput.get();
            } else {
                return output;
            }
        }
    }

    private void checkIndex(int index) {
        if (index >= numberOfOutputs) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + numberOfOutputs);
        }
    }

    @Override
    public List<TransactionOutput> getOutputs() {
        return getOutputs(true);
    }

    public List<TransactionOutput> getOutputs(boolean includeEmptyOutputs) {
        if (trimmedOutputs == null) {
            return super.getOutputs();
        } else {
            ImmutableList.Builder<TransactionOutput> listBuilder = ImmutableList.builder();
            TransactionOutput emptyOutput = EmptyTransactionOutput.get();
            for (int i = 0; i < numberOfOutputs; i++) {
                TransactionOutput output = trimmedOutputs.get(i);
                // Trimmed outputs are empty
                if (output == null && includeEmptyOutputs) {
                    listBuilder.add(emptyOutput);
                } else if (output != null) {
                    listBuilder.add(output);
                }
            }
            return listBuilder.build();
        }
    }

    public boolean hasAllOutputs() {
        return trimmedOutputs == null;
    }

    @Override
    public byte[] unsafeBitcoinSerialize() {
        throw new IllegalArgumentException("Cannot serialize trimmed transaction");
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream, boolean includeExtensions) throws IOException {
        throw new IllegalArgumentException("Cannot serialize trimmed transaction");
    }
}
