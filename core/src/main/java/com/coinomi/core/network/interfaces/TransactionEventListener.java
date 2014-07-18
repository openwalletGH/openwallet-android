package com.coinomi.core.network.interfaces;

import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.ServerClient;
import com.google.bitcoin.core.Address;

import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
public interface TransactionEventListener {
    void onAddressStatusUpdate(AddressStatus status);
    void onUnspentTransactionUpdate(AddressStatus status,
                                    List<ServerClient.Transaction> transactions);
}
