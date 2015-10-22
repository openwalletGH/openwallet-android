package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.util.AddressUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.WalletAccount;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * Created by vbcs on 1/10/2015.
 */
public class BitTransaction extends AbstractTransaction<Transaction> {
    public BitTransaction(Transaction transaction) {
        super(transaction);
    }

    @Override
    public TransactionConfidence.ConfidenceType getConfidenceType() {
        return transaction.getConfidence().getConfidenceType();
    }

    @Override
    public int getAppearedAtChainHeight() {
        return transaction.getConfidence().getAppearedAtChainHeight();
    }

    @Override
    public TransactionConfidence.Source getSource() {
        return transaction.getConfidence().getSource();
    }

    @Override
    public int getDepthInBlocks() {
        return transaction.getConfidence().getDepthInBlocks();
    }

    @Override
    public String getHashAsString() {
        return transaction.getHashAsString();
    }

    @Override
    public Value getValue(AbstractWallet wallet) {
        return Value.valueOf(wallet.getCoinType(), transaction.getValue(wallet));

    }

    @Override
    public TxMessage getMessage() {
        return null;
    }

    @Override
    public Value getFee(WalletAccount wallet) {
        return Value.valueOf(wallet.getCoinType(), transaction.getFee());
    }

    @Override
    public List<Map.Entry<AbstractAddress, Value>> getOutputs(AbstractWallet wallet) {
        List<Map.Entry<AbstractAddress, Value>> outputs = new ArrayList<>();
        for ( TransactionOutput output : transaction.getOutputs() )
        {
            outputs.add(
                    new AbstractMap.SimpleEntry<AbstractAddress, Value>
                            (AddressUtils.fromScript(wallet.getCoinType(), output.getScriptPubKey()),
                                    Value.valueOf(wallet.getCoinType(),output.getValue())));
        }
    return outputs;
    }

    @Override
    public byte[] getHash() {
        return transaction.getHash().getBytes();
    }

    @Override
    public boolean isCoinBase() {
        return transaction.isCoinBase();
    }

    @Override
    public boolean isCoinStake() {
        return transaction.isCoinStake();
    }

    @Override
    public boolean isMine(WalletAccount wallet, Map.Entry<AbstractAddress, Value> output) {

        return wallet.getActiveAddresses().contains(output.getKey()) ;
    }

    @Override
    public Transaction getTransaction() {
        return transaction;
    }


//    public BitTransaction(Transaction tx) {
//        super(tx);
//    }

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
