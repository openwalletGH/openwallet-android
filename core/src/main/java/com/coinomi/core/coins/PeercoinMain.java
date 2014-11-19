package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class PeercoinMain extends CoinType {
    private PeercoinMain() {
        id = "peercoin.main";
        uid = 41;

        addressHeader = 55;
        p2shHeader = 117;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Peercoin (beta)";
        symbol = "PPC";
        uriScheme = "peercoin"; // TODO verify, could be ppcoin?
        bip44Index = 4;
        feePerKb = Coin.valueOf(10000); // 0.01PPC, careful Peercoin has 1000000 units per coin
        minNonDust = Coin.valueOf(1); //TODO verify
        unitExponent = 6;
    }

    private static PeercoinMain instance = new PeercoinMain();
    public static synchronized PeercoinMain get() {
        return instance;
    }
}
