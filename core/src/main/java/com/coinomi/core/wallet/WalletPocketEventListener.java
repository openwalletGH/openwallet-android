package com.coinomi.core.wallet;

import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public interface WalletPocketEventListener {

    public void onNewBalance(Coin newBalance);

}
