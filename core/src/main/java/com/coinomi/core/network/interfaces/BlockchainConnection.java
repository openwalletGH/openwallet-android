package com.coinomi.core.network.interfaces;

import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.wallet.AbstractAddress;

import org.bitcoinj.core.Sha256Hash;

import java.util.List;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public interface BlockchainConnection<T> {
    void getBlock(int height, TransactionEventListener<T> listener);

    void subscribeToBlockchain(final TransactionEventListener<T> listener);

    void subscribeToAddresses(List<AbstractAddress> addresses,
                              TransactionEventListener<T> listener);

    void getHistoryTx(AddressStatus status, TransactionEventListener<T> listener);

    void getTransaction(Sha256Hash txHash, TransactionEventListener<T> listener);

    void broadcastTx(final T tx, final TransactionEventListener<T> listener);

    boolean broadcastTxSync(final T tx);

    void ping(@Nullable String versionString);

    void addEventListener(ConnectionEventListener listener);

    void resetConnection();

    void stopAsync();

    boolean isActivelyConnected();

    void startAsync();


}
