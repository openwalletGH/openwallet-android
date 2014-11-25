package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class BitcoinTest extends CoinType {
    private BitcoinTest() {
        id = "bitcoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;

        name = "Bitcoin Test";
        symbol = "BTC";
        uriScheme = "bitcoin";
        bip44Index = 1;
        feePerKb = Coin.valueOf(1000);
        minNonDust = Coin.valueOf(546);
        unitExponent = 8;
    }

    private static BitcoinTest instance = new BitcoinTest();
    public static synchronized BitcoinTest get() {
        return instance;
    }
}
