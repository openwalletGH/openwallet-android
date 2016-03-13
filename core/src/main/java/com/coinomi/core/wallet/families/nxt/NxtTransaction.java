package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.nxt.Transaction;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.google.common.collect.ImmutableList;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionConfidence;

import java.util.List;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author vbcs
 * @author John L. Jegutanis
 */
public final class NxtTransaction implements AbstractTransaction {
    final CoinType type;
    Sha256Hash hash;
    final Transaction tx;

    TransactionConfidence.ConfidenceType confidence = TransactionConfidence.ConfidenceType.BUILDING;

    public NxtTransaction(CoinType type, Transaction transaction) {
        this.type = type;
        tx = checkNotNull(transaction);
    }

    public void setConfidenceType(TransactionConfidence.ConfidenceType conf) {
        confidence = conf;
    }

    @Override
    public CoinType getType() {
        return type;
    }

    @Override
    public TransactionConfidence.ConfidenceType getConfidenceType() {
        return (tx.getConfirmations() > 0 ) ? confidence : TransactionConfidence.ConfidenceType.PENDING;
    }

    @Override
    public int getAppearedAtChainHeight() {
        return tx.getHeight();
    }

    @Override
    public void setAppearedAtChainHeight(int appearedAtChainHeight) {
        tx.setHeight(appearedAtChainHeight);
    }

    @Override
    public TransactionConfidence.Source getSource() {
        return TransactionConfidence.Source.NETWORK;
    }

    @Override
    public void setSource(TransactionConfidence.Source source) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int getDepthInBlocks() {
        return tx.getConfirmations();
    }

    @Override
    public Value getValue(AbstractWallet wallet) {
        if (tx.getSenderId() == wallet.getReceiveAddress().getId()) {
            return Value.valueOf(wallet.getCoinType(), -1 * tx.getAmountNQT());
        } else {
            return Value.valueOf(wallet.getCoinType(), tx.getAmountNQT());
        }
    }

    @Override
    public TxMessage getMessage() {
        if (tx.getMessage() != null) {
            return new NxtTxMessage(tx);
        }
        return null;
    }

    @Override
    public Value getFee() {
        return type.value(tx.getFeeNQT());
    }

    @Override
    public List<AbstractOutput> getSentTo() {
        return ImmutableList.of(new AbstractOutput(new NxtAddress(type, tx.getRecipientId()),
                Value.valueOf(type, tx.getAmountNQT())));
    }

    @Override
    public List<AbstractAddress> getReceivedFrom() {
        return ImmutableList.of((AbstractAddress) new NxtAddress(type, tx.getSenderId()));
    }

    @Override
    public Sha256Hash getHash() {
        if (hash == null) {
            hash = new Sha256Hash(tx.getFullHash());
        }
        return hash;
    }

    @Override
    public byte[] getHashBytes() {
        return getHash().getBytes();
    }

    @Override
    public void setDepthInBlocks(int depthInBlocks) {

    }

    @Override
    public long getTimestamp() {
        return tx.getTimestamp(); // TODO use block timestamp instead
//        return tx.getBlockTimestamp();
    }

    @Override
    public void setTimestamp(long timestamp) {
        throw new RuntimeException("NxtTransaction::setTimestamp not implemented");
    }

    @Override
    public String getHashAsString() {
        return getHash().toString();
    }

    @Override
    public boolean isGenerated() {
        return false;
    }

    @Override
    public boolean isTrimmed() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NxtTransaction other = (NxtTransaction) o;
        return getHash().equals(other.getHash());
    }

    public Transaction getRawTransaction() {
        return tx;
    }


    /*public NxtTransaction(WalletAccount account, Transaction tx) {
        super(account, tx);
    }

    @Override
    public void parse(WalletAccount account, Object tx) {
        if (tx != null) {
            long amount = (account.getReceiveAddress().getId() == ((Transaction)tx).getSenderId()) ?
                    -1 * ((Transaction)tx).getAmountNQT() + -1 * ((Transaction)tx).getFeeNQT() : ((Transaction)tx).getAmountNQT();

            this.value = type.value(amount);

            this.fee = type.value(((Transaction)tx).getFeeNQT());

            if (account.getReceiveAddress().getId() == ((Transaction)tx).getSenderId()) {
                AbstractAddress address = new NxtFamilyAddress(this.type, ((Transaction)tx).getRecipientId());
                Value value = type.value(((Transaction)tx).getFeeNQT() + ((Transaction)tx).getAmountNQT());
                outputs.put(address, value);
            }
            else if (account.getReceiveAddress().getId() == ((Transaction)tx).getRecipientId()) {
                AbstractAddress address = account.getReceiveAddress();
                Value value = type.value(((Transaction)tx).getFeeNQT() + ((Transaction)tx).getAmountNQT());
                outputs.put(address, value);
            }
            hashString = ((Transaction) tx).getFullHash();

        }

        parsed = true;
    }

    /*@Override
    public byte[] serialize() {
        checkNotNull(tx, "Cannot serialize null transaction");
        return ((Transaction) tx).getBytes();
    }
    */
}
