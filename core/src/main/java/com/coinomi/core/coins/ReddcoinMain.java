package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class ReddcoinMain extends CoinType {
    public ReddcoinMain() {
        id = "reddcoin.main";
        uid = -1; // FIXME

        addressHeader = 61;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Reddcoin";
        symbol = "RDD";
        uriScheme = "reddcoin";
        bip44Index = 11;
        // TODO set correct values
        feePerKb = Coin.valueOf(1);
        minNonDust = Coin.valueOf(1);
        unitExponent = 8;
        throw new RuntimeException(name+" bip44Index " + bip44Index + "is not standardized");
    }

    private static ReddcoinMain instance;
    public static synchronized ReddcoinMain get() {
        if (instance == null) {
            instance = new ReddcoinMain();
        }
        return instance;
    }
}
