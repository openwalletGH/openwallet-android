package com.coinomi.core;

import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public interface WalletPocketEventListener {

    public void onNewBalance(Coin newBalance);

}
