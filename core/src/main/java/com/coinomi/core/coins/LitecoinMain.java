package com.coinomi.core.coins;

import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class LitecoinMain extends CoinType {
    public LitecoinMain() {
        id = "litecoin.main";

        addressHeader = 48;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Litecoin";
        symbol = "LTC";
        uriScheme = "litecoin";
        bip44Index = 2;
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1000);
    }

    private static LitecoinMain instance;
    public static synchronized LitecoinMain get() {
        if (instance == null) {
            instance = new LitecoinMain();
        }
        return instance;
    }
}
