package com.openwallet.core.network.interfaces;

import com.openwallet.core.coins.CoinType;

/**
 * @author John L. Jegutanis
 */
public interface ConnectionEventListener {
    void onConnection(BlockchainConnection blockchainConnection);
    void onDisconnect();
}
