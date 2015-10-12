package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.nxt.Transaction;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.WalletAccount;

import org.bitcoinj.core.TransactionConfidence;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * Created by vbcs on 1/10/2015.
 */
public class NxtTransaction extends AbstractTransaction<Transaction> {
    public NxtTransaction(Transaction transaction) {
        super(transaction);
    }

    @Override
    public TransactionConfidence getConfidence() {
        return null;
    }

    @Override
    public String getHashAsString() {
        return null;
    }

    @Override
    public Value getValue(AbstractWallet wallet) {
        return null;
    }

    @Override
    public TxMessage getMessage() {
        return null;
    }

    @Override
    public Value getFee(AbstractWallet wallet) {
        return null;
    }

    @Override
    public List<Map.Entry<AbstractAddress, Value>> getOutputs(AbstractWallet wallet) {
        return null;
    }

    @Override
    public byte[] getHash() {
        return new byte[0];
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
    public boolean isMine(Map.Entry<AbstractAddress, Value> output) {
        return false;
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
