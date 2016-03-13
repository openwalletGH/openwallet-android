package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.messages.MessageFactory;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.TransactionWatcherWallet;
import com.google.common.collect.ImmutableList;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * @author vbcs
 * @author John L. Jegutanis
 */
public final class BitTransaction implements AbstractTransaction {
    private static final Logger log = LoggerFactory.getLogger(BitTransaction.class);

    final CoinType type;
    final Sha256Hash hash;
    final Transaction tx;
    final boolean isTrimmed;
    final Value valueSent;
    final Value valueReceived;
    final Value value;
    @Nullable final Value fee;

    public BitTransaction(Sha256Hash transactionId, Transaction transaction, boolean isTrimmed,
                          Value valueSent, Value valueReceived, @Nullable Value fee) {
        tx = checkNotNull(transaction);
        type = (CoinType) tx.getParams();
        this.isTrimmed = isTrimmed;
        if (isTrimmed) {
            hash = checkNotNull(transactionId);
            this.valueSent = checkNotNull(valueSent);
            this.valueReceived = checkNotNull(valueReceived);
            this.value = valueReceived.subtract(valueSent);
            this.fee = fee;
        } else {
            hash = null;
            this.valueSent = null;
            this.valueReceived = null;
            this.value = null;
            this.fee = null;
        }
    }

    public BitTransaction(Transaction transaction) {
        this(checkNotNull(transaction).getHash(), transaction, false, null, null, null);
    }

    public BitTransaction(CoinType type, byte[] rawTx) {
        this(new Transaction(type, rawTx));
    }

    public static BitTransaction fromTrimmed(Sha256Hash transactionId, Transaction transaction,
                                             Value valueSent, Value valueReceived, Value fee) {
        return new BitTransaction(transactionId, transaction, true, valueSent, valueReceived, fee);
    }

    @Override
    public CoinType getType() {
        return type;
    }

    @Override
    public TransactionConfidence.ConfidenceType getConfidenceType() {
        return tx.getConfidence().getConfidenceType();
    }

    @Override
    public void setConfidenceType(TransactionConfidence.ConfidenceType type) {
        tx.getConfidence().setConfidenceType(type);
    }

    @Override
    public int getAppearedAtChainHeight() {
        return tx.getConfidence().getAppearedAtChainHeight();
    }

    @Override
    public void setAppearedAtChainHeight(int appearedAtChainHeight) {
        tx.getConfidence().setAppearedAtChainHeight(appearedAtChainHeight);
    }

    @Override
    public TransactionConfidence.Source getSource() {
        return tx.getConfidence().getSource();
    }

    @Override
    public void setSource(TransactionConfidence.Source source) {
        tx.getConfidence().setSource(source);
    }

    @Override
    public int getDepthInBlocks() {
        return tx.getConfidence().getDepthInBlocks();
    }

    @Override
    public void setDepthInBlocks(int depthInBlocks) {
        tx.getConfidence().setDepthInBlocks(depthInBlocks);
    }

    @Override
    public long getTimestamp() {
        return tx.getUpdateTime().getTime();
    }

    @Override
    public void setTimestamp(long timestamp) {
        tx.setUpdateTime(new Date(timestamp));
    }

    @Override
    public Value getValue(AbstractWallet abstractWallet) {
        if (isTrimmed) {
            return value;
        } else {
            if (abstractWallet instanceof TransactionWatcherWallet) {
                TransactionWatcherWallet wallet = (TransactionWatcherWallet) abstractWallet;
                return getValueReceived(wallet).subtract(getValueSent(wallet));
            } else {
                return type.value(0);
            }
        }
    }

    public Value getValueReceived(TransactionWatcherWallet wallet) {
        if (isTrimmed) {
            return getValueReceived();
        } else {
            return type.value(tx.getValueSentToMe(wallet));
        }
    }

    public Value getValueSent(TransactionWatcherWallet wallet) {
        if (isTrimmed) {
            return getValueSent();
        } else {
            tx.ensureParsed();
            // Find the value of the inputs that draw value from the wallet
            Value sent = type.value(0);
            Map<Sha256Hash, BitTransaction> transactions = wallet.getTransactions();
            for (TransactionInput input : tx.getInputs()) {
                TransactionOutPoint outPoint = input.getOutpoint();
                // This input is taking value from a transaction in our wallet. To discover the value,
                // we must find the connected transaction.
                OutPointOutput connected = wallet.getUnspentTxOutput(outPoint);
                if (connected == null) {
                    BitTransaction spendingTx = transactions.get(outPoint.getHash());
                    int index = (int) outPoint.getIndex();
                    if (spendingTx != null && spendingTx.isOutputAvailable(index)) {
                        connected = new OutPointOutput(spendingTx, index);
                    }
                }

                if (connected == null)
                    continue;

                // The connected output may be the change to the sender of a previous input sent to this wallet. In this
                // case we ignore it.
                if (!connected.getOutput().isMineOrWatched(wallet))
                    continue;

                sent = sent.add(connected.getValue());
            }

            return sent;
        }
    }

    public Value getValueReceived() {
        return isTrimmed ? valueReceived : type.value(0);
    }

    public Value getValueSent() {
        return isTrimmed ? valueSent : type.value(0);
    }

    @Override
    @Nullable
    public Value getFee() {
        return isTrimmed ? fee : type.value(tx.getFee());
    }

    @Nullable
    public Value getRawTxFee(TransactionWatcherWallet wallet) {
        checkState(!isTrimmed, "Cannot get raw tx fee from a trimmed transaction");
        Value fee = type.value(0);
        for (TransactionInput input : tx.getInputs()) {
            TransactionOutPoint outPoint = input.getOutpoint();
            BitTransaction inTx = wallet.getTransaction(outPoint.getHash());
            if (inTx == null || !inTx.isOutputAvailable((int) outPoint.getIndex())) {
                return null;
            }
            TransactionOutput txo = inTx.getOutput((int) outPoint.getIndex());
            fee = fee.add(txo.getValue());
        }
        for (TransactionOutput output : getOutputs()) {
            fee = fee.subtract(output.getValue());
        }
        return fee;
    }

    @Override
    public TxMessage getMessage() {
        MessageFactory messageFactory = type.getMessagesFactory();
        if (messageFactory != null) {
            return messageFactory.extractPublicMessage(this);
        } else {
            return null;
        }
    }

    @Override
    @Nullable
    public List<AbstractAddress> getReceivedFrom() {
        return ImmutableList.of();
    }

    @Override
    public List<AbstractOutput> getSentTo() {
        List<AbstractOutput> outputs = new ArrayList<>();
        for (TransactionOutput output : getOutputs(false)) {
            try {
                AbstractAddress address = BitAddress.from(type, output.getScriptPubKey());
                outputs.add(new AbstractOutput(address, type.value(output.getValue())));
            } catch (Exception e) { /* ignore this output */ }
        }
        return outputs;
    }

    @Override
    public Sha256Hash getHash() {
        return isTrimmed ? hash : tx.getHash();
    }

    @Override
    public String getHashAsString() {
        return getHash().toString();
    }

    @Override
    public byte[] getHashBytes() {
        return getHash().getBytes();
    }

    @Override
    public boolean isGenerated() {
        return tx.isCoinBase() || tx.isCoinStake();
    }

    @Override
    public boolean isTrimmed() {
        return isTrimmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitTransaction other = (BitTransaction) o;
        return getHash().equals(other.getHash());
    }

    public Transaction getRawTransaction() {
        return tx;
    }

    public List<TransactionInput> getInputs() {
        return tx.getInputs();
    }

    public TransactionOutput getOutput(int index) {
        return tx.getOutput(index);
    }

    public List<TransactionOutput> getOutputs() {
        return getOutputs(true);
    }

    public List<TransactionOutput> getOutputs(boolean includeEmptyOutputs) {
        if (tx instanceof TrimmedTransaction) {
            return ((TrimmedTransaction) tx).getOutputs(includeEmptyOutputs);
        } else {
            return tx.getOutputs();
        }
    }

    private boolean isOutputAvailable(int index) {
        if (tx instanceof TrimmedTransaction) {
            return ((TrimmedTransaction) tx).isOutputAvailable(index);
        } else {
            return index < getNumberOfOutputs();
        }
    }

    private long getNumberOfOutputs() {
        if (tx instanceof TrimmedTransaction) {
            return ((TrimmedTransaction) tx).getNumberOfOutputs();
        } else {
            return tx.getOutputs().size();
        }
    }

    public byte[] bitcoinSerialize() {
        checkState(!isTrimmed, "Cannot serialize a trimmed transaction");
        return tx.bitcoinSerialize();
    }

    private BitTransaction getTrimTransaction(TransactionBag wallet) {
        throw new RuntimeException();
//        BitTransaction transaction = rawTransactions.get(hash);
//
//        if (transaction == null || transaction.isTrimmed()) return false;
//
//        final Value valueSent = transaction.getValueSent(this);
//        final Value valueReceived = transaction.getValueReceived(this);
//        final Value fee = transaction.getFee();
//
//        WalletTransaction.Pool txPool;
//        if (confirmed.containsKey(hash)) {
//            txPool = WalletTransaction.Pool.CONFIRMED;
//        } else if (pending.containsKey(hash)) {
//            txPool = WalletTransaction.Pool.PENDING;
//        } else {
//            throw new RuntimeException("Transaction is not found in any pool");
//        }
//
//        Transaction txFull = transaction.getRawTransaction();
//        Transaction tx = new Transaction(type);
//        tx.getConfidence().setSource(txFull.getConfidence().getSource());
//        tx.getConfidence().setConfidenceType(txFull.getConfidence().getConfidenceType());
//        if (tx.getConfidence().getConfidenceType() == BUILDING) {
//            tx.getConfidence().setAppearedAtChainHeight(txFull.getConfidence().getAppearedAtChainHeight());
//            tx.getConfidence().setDepthInBlocks(txFull.getConfidence().getDepthInBlocks());
//        }
//        tx.setTime(txFull.getTime());
//        tx.setTokenId(txFull.getTokenId());
//        tx.setExtraBytes(txFull.getExtraBytes());
//        tx.setUpdateTime(txFull.getUpdateTime());
//        tx.setLockTime(txFull.getLockTime());
//
//        if (txFull.getAppearsInHashes() != null) {
//            for (Map.Entry<Sha256Hash, Integer> appears : txFull.getAppearsInHashes().entrySet()) {
//                tx.addBlockAppearance(appears.getKey(), appears.getValue());
//            }
//        }
//
//        tx.setPurpose(txFull.getPurpose());
//
//        // Remove unrelated outputs when receiving coins
//        boolean isReceiving = valueReceived.compareTo(valueSent) > 0;
//        if (isReceiving) {
//            for (TransactionOutput output : txFull.getOutputs()) {
//                if (output.isMineOrWatched(this)) {
//                    tx.addOutput(output);
//                }
//            }
//        } else {
//            // When sending keep all outputs
//            for (TransactionOutput output : txFull.getOutputs()) {
//                tx.addOutput(output);
//            }
//        }
//
//        return BitTransaction.fromTrimmed(hash, tx, valueSent, valueReceived, fee);
    }
}
