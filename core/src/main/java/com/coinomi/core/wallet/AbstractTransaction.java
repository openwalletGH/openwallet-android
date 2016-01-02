package com.coinomi.core.wallet;

import com.coinomi.core.coins.Value;
import com.coinomi.core.messages.TxMessage;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionConfidence;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public interface AbstractTransaction extends Serializable {
    TransactionConfidence.ConfidenceType getConfidenceType();

    void setConfidenceType(TransactionConfidence.ConfidenceType type);

    int getAppearedAtChainHeight();

    void setAppearedAtChainHeight(int appearedAtChainHeight);

    TransactionConfidence.Source getSource();
    int getDepthInBlocks();
    Sha256Hash getHash();
    String getHashAsString();
    byte[] getHashBytes();

    void setDepthInBlocks(int depthInBlocks);

    Value getValue(AbstractWallet wallet);
    TxMessage getMessage();
    Value getFee();
    @Nullable AbstractAddress getSender(AbstractWallet wallet);
    List<Map.Entry<AbstractAddress, Value>> getSentTo(AbstractWallet wallet);
    // Coin base or coin stake transaction
    boolean isGenerated();
    // If this transaction has trimmed irrelevant data to save space
    boolean isTrimmed();
    boolean isMine(WalletAccount wallet, Map.Entry<AbstractAddress, Value> output);
    String toString();
}
