package com.coinomi.core.wallet;

import org.bitcoinj.core.*;
import org.bitcoinj.core.Wallet;

/**
 * @author John L. Jegutanis
 */
public interface WalletPocketEventListener {

    void onNewBalance(Coin newBalance, Coin pendingAmount);

    void onNewBlock(WalletPocket pocket);

    void onTransactionConfidenceChanged(WalletPocket pocket, Transaction tx);

    void onTransactionBroadcastFailure(WalletPocket pocket, Transaction tx);

    void onTransactionBroadcastSuccess(WalletPocket pocket, Transaction tx);

    void onPocketChanged(final WalletPocket pocket);

    void onConnectivityStatus(final WalletPocketConnectivity pocketConnectivity);
}
