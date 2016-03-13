package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.messages.TxMessage;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.TransactionConfidence.Source;

import java.io.Serializable;
import java.util.List;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public interface AbstractTransaction extends Serializable {
    class AbstractOutput {
        final AbstractAddress abstractAddress;
        final Value value;

        public AbstractOutput(AbstractAddress abstractAddress, Value value) {
            this.abstractAddress = abstractAddress;
            this.value = value;
        }

        public AbstractAddress getAddress() {
            return abstractAddress;
        }

        public Value getValue() {
            return value;
        }
    }

    CoinType getType();

    Sha256Hash getHash();
    String getHashAsString();
    byte[] getHashBytes();

    ConfidenceType getConfidenceType();
    void setConfidenceType(ConfidenceType type);

    int getAppearedAtChainHeight();
    void setAppearedAtChainHeight(int appearedAtChainHeight);

    Source getSource();
    void setSource(Source source);

    int getDepthInBlocks();
    void setDepthInBlocks(int depthInBlocks);

    long getTimestamp();
    void setTimestamp(long timestamp);

    Value getValue(AbstractWallet wallet);
    @Nullable Value getFee();
    @Nullable TxMessage getMessage();
    List<AbstractAddress> getReceivedFrom();
    List<AbstractOutput> getSentTo();
    // Coin base or coin stake transaction
    boolean isGenerated();
    // If this transaction has trimmed irrelevant data to save space
    boolean isTrimmed();
    String toString();
    boolean equals(Object o);
}
