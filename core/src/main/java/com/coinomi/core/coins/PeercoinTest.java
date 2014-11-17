package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class PeercoinTest extends CoinType {
    public PeercoinTest() {
        id = "peercoin.test";
        uid = 42;

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Peercoin Test";
        symbol = "PPC";
        uriScheme = "peercoin"; // TODO verify, could be ppcoin?
        bip44Index = 1;
        feePerKb = Coin.valueOf(10000); // 0.01PPC, careful Peercoin has 1000000 units per coin
        minNonDust = Coin.valueOf(1); //TODO verify
        unitExponent = 6;
    }

    private static PeercoinTest instance;
    public static synchronized PeercoinTest get() {
        if (instance == null) {
            instance = new PeercoinTest();
        }
        return instance;
    }
}
