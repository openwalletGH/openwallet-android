package com.coinomi.core.network.interfaces;

import com.coinomi.core.network.ServerClient;

/**
 * @author Giannis Dzegoutanis
 */
public interface ConnectionEventListener {
    void onConnection(ServerClient serverClient);
    void onDisconnect();
}
