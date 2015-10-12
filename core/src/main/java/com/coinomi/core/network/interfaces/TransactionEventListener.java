package com.coinomi.core.network.interfaces;

import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.ServerClient;
import org.bitcoinj.core.Transaction;

import java.util.List;

/**
 * @author John L. Jegutanis
 */
public interface TransactionEventListener<T> {
    void onNewBlock(BlockHeader header);

    void onAddressStatusUpdate(AddressStatus status);

//    void onUnspentTransactionUpdate(AddressStatus status, List<ServerClient.UnspentTx> unspentTxes);

    void onTransactionHistory(AddressStatus status, List<ServerClient.HistoryTx> historyTxes);

    void onTransactionUpdate(T tx);

    void onTransactionBroadcast(T transaction);

    void onTransactionBroadcastError(T tx);
}
