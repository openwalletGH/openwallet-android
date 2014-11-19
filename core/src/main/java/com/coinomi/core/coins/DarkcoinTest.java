package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class DarkcoinTest extends CoinType {
    private DarkcoinTest() {
        id = "darkcoin.test";
        uid = 52;

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Darkcoin Test";
        symbol = "DRK";
        uriScheme = "darkcoin";
        bip44Index = 1;
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1); //TODO verify
        unitExponent = 8;
    }

    private static DarkcoinTest instance = new DarkcoinTest();
    public static synchronized DarkcoinTest get() {
        return instance;
    }
}
