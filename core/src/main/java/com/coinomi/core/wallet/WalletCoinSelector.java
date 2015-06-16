package com.coinomi.core.wallet;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.wallet.DefaultCoinSelector;

/**
 * @author John L. Jegutanis
 */
public class WalletCoinSelector extends DefaultCoinSelector {
    @Override
    protected boolean shouldSelect(Transaction tx) {
        return isSelectable(tx);
    }

    public static boolean isSelectable(Transaction tx) {
        // Pick any transaction
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return type.equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                type.equals(TransactionConfidence.ConfidenceType.PENDING);
    }
}
