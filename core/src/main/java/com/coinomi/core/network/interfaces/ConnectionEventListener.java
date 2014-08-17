package com.coinomi.core.network.interfaces;

import com.coinomi.core.network.BlockchainConnection;

/**
 * @author Giannis Dzegoutanis
 */
public interface ConnectionEventListener {
    void onConnection(BlockchainConnection blockchainConnection);
    void onDisconnect();
}
