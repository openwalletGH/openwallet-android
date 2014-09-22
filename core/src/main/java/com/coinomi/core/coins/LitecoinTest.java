package com.coinomi.core.coins;

import com.coinomi.core.Constants;
import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class LitecoinTest extends CoinType {
    public LitecoinTest() {
        id = "litecoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Litecoin Test";
        symbol = "LTC";
        uriScheme = "litecoin";
        bip44Index = 1;
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1000);
    }

    private static LitecoinTest instance;
    public static synchronized LitecoinTest get() {
        if (instance == null) {
            instance = new LitecoinTest();
        }
        return instance;
    }
}
