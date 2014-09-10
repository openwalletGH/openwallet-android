package com.coinomi.core.wallet;

import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;

import java.util.HashMap;

/**
 * @author Giannis Dzegoutanis
 */
public class TransactionMap extends HashMap<Sha256Hash, Transaction> {

    public Transaction put(Transaction tx) {
        return put(tx.getHash(), tx);
    }

    public boolean containsKey(Transaction tx) {
        return containsKey(tx.getHash());
    }

    public boolean containsKey(Sha256Hash hash) {
        return super.containsKey(hash);
    }
}
