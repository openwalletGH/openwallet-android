package com.coinomi.wallet;

import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;

import java.io.File;

/**
 * @author Giannis Dzegoutanis
 */
public interface Wallet {
    int getLastBlockSeenHeight();

    Transaction getTransaction(Sha256Hash hash);

    void saveToFile(File walletFile);
}
