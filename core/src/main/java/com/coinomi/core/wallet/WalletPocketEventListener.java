package com.coinomi.core.wallet;

import org.bitcoinj.core.*;
import org.bitcoinj.core.Wallet;

/**
 * @author Giannis Dzegoutanis
 */
public interface WalletPocketEventListener {

    void onNewBalance(Coin newBalance, Coin pendingAmount);

    void onNewBlock(WalletPocket pocket);

    void onTransactionConfidenceChanged(WalletPocket pocket, Transaction tx);

    void onPocketChanged(final WalletPocket pocket);

    void onConnectivityStatus(final WalletPocketConnectivity pocketConnectivity);
}
