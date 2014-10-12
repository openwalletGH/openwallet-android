package com.coinomi.core.network;

import com.coinomi.core.coins.CoinType;
import com.coinomi.stratumj.ServerAddress;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
final public class CoinAddress {
    final private CoinType parameters;
    final private List<ServerAddress> addresses;

    public CoinAddress(CoinType parameters, ServerAddress address) {
        this.parameters = parameters;
        this.addresses = ImmutableList.of(address);
    }

    public CoinAddress(CoinType parameters, ServerAddress... addresses) {
        this.parameters = parameters;
        this.addresses = ImmutableList.copyOf(addresses);
    }

    public CoinAddress(CoinType parameters, List<ServerAddress> addresses) {
        this.parameters = parameters;
        this.addresses = ImmutableList.copyOf(addresses);
    }

    public CoinType getParameters() {
        return parameters;
    }

    public List<ServerAddress> getAddresses() {
        return addresses;
    }
}
