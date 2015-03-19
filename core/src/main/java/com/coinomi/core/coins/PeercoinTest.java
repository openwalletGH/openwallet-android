package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class PeercoinTest extends CoinType {
    private PeercoinTest() {
        id = "peercoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;

        name = "Peercoin Test";
        symbol = "PPCTEST";
        uriScheme = "peercoin"; // TODO verify, could be ppcoin?
        bip44Index = 1;
        unitExponent = 6;
        feePerKb = Coin.valueOf(10000); // 0.01PPC, careful Peercoin has 1000000 units per coin
        minNonDust = Coin.valueOf(1);
        softDustLimit = Coin.valueOf(10000); // 0.01PPC, careful Peercoin has 1000000 units per coin
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
    }

    private static PeercoinTest instance = new PeercoinTest();
    public static synchronized PeercoinTest get() {
        return instance;
    }
}
