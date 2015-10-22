package com.coinomi.core.network.interfaces;

import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.wallet.AbstractAddress;

import org.bitcoinj.core.Sha256Hash;

import java.util.List;

/**
 * @author John L. Jegutanis
 */
public interface BlockchainConnection<T> {
    void subscribeToBlockchain(final TransactionEventListener listener);

    void subscribeToAddresses(List<AbstractAddress> addresses,
                              TransactionEventListener listener);

//    void getUnspentTx(AddressStatus status, TransactionEventListener listener);

    void getHistoryTx(AddressStatus status, TransactionEventListener listener);

    void getTransaction(Sha256Hash txHash, TransactionEventListener listener);

    void broadcastTx(final T tx, final TransactionEventListener listener);

    boolean broadcastTxSync(final T tx);

    void ping();

    void addEventListener(ConnectionEventListener listener);

    void resetConnection();

    void stopAsync();

    boolean isActivelyConnected();

    void startAsync();


}
