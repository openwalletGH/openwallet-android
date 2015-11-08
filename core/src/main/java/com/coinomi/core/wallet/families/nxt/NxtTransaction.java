package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.Transaction;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.network.NxtServerClient;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.WalletAccount;

import org.bitcoinj.core.TransactionConfidence;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.coinomi.core.Preconditions.checkNotNull;
import static java.util.AbstractMap.*;

/**
 * Created by vbcs on 1/10/2015.
 */
public class NxtTransaction extends AbstractTransaction<Transaction> {


    TransactionConfidence.ConfidenceType confidence = TransactionConfidence.ConfidenceType.BUILDING;;

    public NxtTransaction(Transaction transaction) {
        super(transaction);
    }

    public void setConfidenceType(TransactionConfidence.ConfidenceType conf) {
        confidence = conf;
    }

    @Override
    public TransactionConfidence.ConfidenceType getConfidenceType() {
        return (transaction.getConfirmations() > 0 ) ? confidence : TransactionConfidence.ConfidenceType.PENDING;
    }

    @Override
    public int getAppearedAtChainHeight() {
        return transaction.getHeight();
    }

    @Override
    public TransactionConfidence.Source getSource() {
        return TransactionConfidence.Source.NETWORK;
    }

    @Override
    public int getDepthInBlocks() {
        return transaction.getConfirmations();
    }

    @Override
    public String getHashAsString() {
        return transaction.getFullHash();
    }

    @Override
    public Value getValue(AbstractWallet wallet) {
        if (transaction.getSenderId() == wallet.getReceiveAddress().getId()) {
            return Value.valueOf(wallet.getCoinType(), -1 * transaction.getAmountNQT());
        } else {
            return Value.valueOf(wallet.getCoinType(), transaction.getAmountNQT());
        }
    }

    @Override
    public TxMessage getMessage() {
        if ( transaction.getMessage() != null ) {
            NxtTxMessage message = new NxtTxMessage(transaction);
            return message;
        }
        return null;
    }

    @Override
    public Value getFee(WalletAccount wallet) {
        return Value.valueOf(wallet.getCoinType(), transaction.getFeeNQT());
    }

    @Override
    public List<Map.Entry<AbstractAddress, Value>> getOutputs(AbstractWallet wallet) {
        List<Map.Entry<AbstractAddress, Value>> outputs = new ArrayList<>();
        outputs.add(new AbstractMap.SimpleEntry<AbstractAddress, Value>
                (new NxtFamilyAddress(wallet.getCoinType(), transaction.getRecipientId()),
                        Value.valueOf(wallet.getCoinType(), transaction.getAmountNQT())));
        return outputs;

    }

    @Override
    public AbstractAddress getSender(AbstractWallet wallet) {
        return new NxtFamilyAddress(wallet.getCoinType(), transaction.getSenderId());
    }

    @Override
    public byte[] getHash() {
        return Convert.parseHexString(transaction.getFullHash());
    }

    @Override
    public boolean isCoinBase() {
        return false;
    }

    @Override
    public boolean isCoinStake() {
        return false;
    }

    @Override
    public boolean isMine(WalletAccount wallet, Map.Entry<AbstractAddress, Value> output) {
        return wallet.getActiveAddresses().contains(output.getKey());
    }

    @Override
    public Transaction getTransaction() {
        return transaction;
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
