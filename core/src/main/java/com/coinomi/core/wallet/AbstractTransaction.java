package com.coinomi.core.wallet;

import com.coinomi.core.coins.Value;
import com.coinomi.core.messages.TxMessage;

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
    int getAppearedAtChainHeight();
    TransactionConfidence.Source getSource();
    int getDepthInBlocks();
    String getHashAsString();
    Value getValue(AbstractWallet wallet);
    TxMessage getMessage();
    Value getFee(WalletAccount wallet);
    @Nullable AbstractAddress getSender(AbstractWallet wallet);
    List<Map.Entry<AbstractAddress, Value>> getOutputs(AbstractWallet wallet);
    byte[] getHash();
    // Coin base or coin stake transaction
    boolean isGenerated();
    boolean isMine(WalletAccount wallet, Map.Entry<AbstractAddress, Value> output);
    @Nullable Object getRawTransaction();
    String toString();
}
