package com.coinomi.core.wallet;

import com.google.bitcoin.core.*;
import com.google.bitcoin.core.Wallet;

/**
 * @author Giannis Dzegoutanis
 */
public interface WalletPocketEventListener {

    void onNewBalance(Coin newBalance, Coin pendingAmount);

    void onTransactionConfidenceChanged(WalletPocket pocket, Transaction tx);

    void onPocketChanged(final WalletPocket pocket);
}
