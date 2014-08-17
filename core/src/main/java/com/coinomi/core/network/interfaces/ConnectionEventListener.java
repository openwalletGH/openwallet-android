package com.coinomi.core.network.interfaces;

/**
 * @author Giannis Dzegoutanis
 */
public interface ConnectionEventListener {
    void onConnection(BlockchainConnection blockchainConnection);
    void onDisconnect();
}
