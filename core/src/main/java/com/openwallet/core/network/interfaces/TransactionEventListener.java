package com.openwallet.core.network.interfaces;

import com.openwallet.core.network.AddressStatus;
import com.openwallet.core.network.BlockHeader;
import com.openwallet.core.network.ServerClient.HistoryTx;

import java.util.List;

/**
 * @author John L. Jegutanis
 */
public interface TransactionEventListener<T> {
    void onNewBlock(BlockHeader header);

    void onBlockUpdate(BlockHeader header);

    void onAddressStatusUpdate(AddressStatus status);

    void onTransactionHistory(AddressStatus status, List<HistoryTx> historyTxes);

    void onTransactionUpdate(T transaction);

    void onTransactionBroadcast(T transaction);

    void onTransactionBroadcastError(T transaction);
}
