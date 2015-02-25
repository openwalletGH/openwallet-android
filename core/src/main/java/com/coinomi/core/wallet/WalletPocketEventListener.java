package com.coinomi.core.wallet;

import org.bitcoinj.core.*;

/**
 * @author John L. Jegutanis
 */
public interface WalletPocketEventListener {

    void onNewBalance(Coin newBalance, Coin pendingAmount);

    void onNewBlock(WalletAccount pocket);

    void onTransactionConfidenceChanged(WalletAccount pocket, Transaction tx);

    void onTransactionBroadcastFailure(WalletAccount pocket, Transaction tx);

    void onTransactionBroadcastSuccess(WalletAccount pocket, Transaction tx);

    void onPocketChanged(final WalletAccount pocket);

    void onConnectivityStatus(final WalletPocketConnectivity pocketConnectivity);
}
