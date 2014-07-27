package com.coinomi.core.network.interfaces;

import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.ServerClient;

import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
public interface TransactionEventListener {
    void onAddressStatusUpdate(AddressStatus status);
    void onUnspentTransactionUpdate(AddressStatus status,
                                    List<ServerClient.UnspentTx> unspentTxes);

    void onTransactionUpdate(ServerClient.UnspentTx tx, byte[] rawTx);
}
