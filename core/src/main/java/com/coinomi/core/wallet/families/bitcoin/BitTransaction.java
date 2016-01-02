package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.util.AddressUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.WalletAccount;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author vbcs
 * @author John L. Jegutanis
 */
public final class BitTransaction implements AbstractTransaction {
    final CoinType type;
    final Sha256Hash hash;
    final Transaction tx;
    final boolean isTrimmed;

    public BitTransaction(Sha256Hash transactionId, Transaction transaction, boolean isTrimmed) {
        tx = checkNotNull(transaction);
        type = (CoinType) tx.getParams();
        hash = transactionId;
        this.isTrimmed = isTrimmed;
    }

    public BitTransaction(Transaction transaction) {
        this(checkNotNull(transaction).getHash(), transaction, false);
    }

    public BitTransaction(CoinType type, byte[] rawTx) {
        this(new Transaction(type, rawTx));
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
    public int getDepthInBlocks() {
        return tx.getConfidence().getDepthInBlocks();
    }

    @Override
    public void setDepthInBlocks(int depthInBlocks) {
        tx.getConfidence().setDepthInBlocks(depthInBlocks);
    }

    @Override
    public Value getValue(AbstractWallet wallet) {
        CoinType type = wallet.getCoinType();
        if (wallet instanceof TransactionBag) {
            return type.value(tx.getValue((TransactionBag) wallet));
        } else {
            return type.value(0);
        }
    }

    @Override
    public TxMessage getMessage() {
        return null;
    }

    @Override
    public Value getFee() {
        return type.value(tx.getFee());
    }

    @Override
    public AbstractAddress getSender(AbstractWallet wallet) {
        return null;
    }

    @Override
    public List<Map.Entry<AbstractAddress, Value>> getSentTo(AbstractWallet wallet) {
        List<Map.Entry<AbstractAddress, Value>> outputs = new ArrayList<>();
        for (TransactionOutput output : tx.getOutputs()) {
            outputs.add(
                    new AbstractMap.SimpleEntry<AbstractAddress, Value>
                            (AddressUtils.fromScript(wallet.getCoinType(), output.getScriptPubKey()),
                                    Value.valueOf(wallet.getCoinType(),output.getValue())));
        }
        return outputs;
    }

    @Override
    public Sha256Hash getHash() {
        return hash;
    }

    @Override
    public String getHashAsString() {
        return hash.toString();
    }

    @Override
    public byte[] getHashBytes() {
        return tx.getHash().getBytes();
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
    public boolean isMine(WalletAccount wallet, Map.Entry<AbstractAddress, Value> output) {

        return wallet.getActiveAddresses().contains(output.getKey()) ;
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
        return tx.getOutputs();
    }

    public byte[] bitcoinSerialize() {
        return tx.bitcoinSerialize();
    }

    /*public BitTransaction(WalletAccount account, Transaction tx) {
        super(account, tx);
    }

    @Override
    public void parse(WalletAccount account, Object tx) {
        if (tx != null) {
            value = type.value(((Transaction) tx).getValue(account));
            fee = type.value(((Transaction) tx).getFee());
            boolean isSending = value.signum() < 0;

            for (TransactionOutput txo : ((Transaction)tx).getOutputs()) {
                if (isSending) {
                    if (txo.isMine(account)) continue;
                    isInternalTransfer = false;
                } else {
                    if (!txo.isMine(account)) continue;
                }

                outputs.put(AddressUtils.fromScript(type, txo.getScriptPubKey()),
                        type.value(txo.getValue()));
            }
            hashString = ((Transaction) tx).getHashAsString();

            inputs = ((Transaction) tx).getInputs();

        }
        parsed = true;
    }

    @Override
    public byte[] serialize() {
        checkNotNull(tx, "Cannot serialize null transaction");
        return ((Transaction) tx).bitcoinSerialize();
    }
    */
}
