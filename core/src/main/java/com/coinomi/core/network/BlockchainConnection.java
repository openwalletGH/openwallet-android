package com.coinomi.core.network;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.google.bitcoin.core.Address;

import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
public interface BlockchainConnection {
    void subscribeToAddresses(CoinType coin, List<Address> addresses,
                              TransactionEventListener listener);

    void getUnspentTx(CoinType coinType, AddressStatus status,
                      TransactionEventListener listener);

    void getTx(CoinType coinType, AddressStatus status, ServerClient.UnspentTx utx, TransactionEventListener listener);

    void ping();
}
