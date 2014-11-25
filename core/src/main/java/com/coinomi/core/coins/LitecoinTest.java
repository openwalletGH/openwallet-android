package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class LitecoinTest extends CoinType {
    private LitecoinTest() {
        id = "litecoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;

        name = "Litecoin Test";
        symbol = "LTC";
        uriScheme = "litecoin";
        bip44Index = 1;
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1000);
        unitExponent = 8;
    }

    private static LitecoinTest instance = new LitecoinTest();
    public static synchronized LitecoinTest get() {
        return instance;
    }
}
