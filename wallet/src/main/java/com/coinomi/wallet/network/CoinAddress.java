package com.coinomi.wallet.network;

import com.coinomi.stratumj.ServerAddress;
import com.coinomi.wallet.coins.Coin;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
final public class CoinAddress {
    final private Coin coin;
    final private List<ServerAddress> addresses;

    public CoinAddress(Coin coin, ServerAddress address) {
        this.coin = coin;
        this.addresses = ImmutableList.of(address);
    }

    public CoinAddress(Coin coin, List<ServerAddress> addresses) {
        this.coin = coin;
        this.addresses = ImmutableList.copyOf(addresses);
    }

    public Coin getCoin() {
        return coin;
    }

    public List<ServerAddress> getAddresses() {
        return addresses;
    }
}
