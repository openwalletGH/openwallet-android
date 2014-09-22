package com.coinomi.core.coins;

import com.coinomi.core.Constants;
import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class BitcoinTest extends CoinType {
    public BitcoinTest() {
        id = "bitcoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Bitcoin Test";
        symbol = "BTC";
        uriScheme = "bitcoin";
        bip44Index = 1;
        feePerKb = Coin.valueOf(10000);
        minNonDust = Coin.valueOf(5460);
    }

    private static BitcoinTest instance;
    public static synchronized BitcoinTest get() {
        if (instance == null) {
            instance = new BitcoinTest();
        }
        return instance;
    }
}
