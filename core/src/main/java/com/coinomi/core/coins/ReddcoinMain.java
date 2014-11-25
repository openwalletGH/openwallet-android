package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class ReddcoinMain extends CoinType {
    private ReddcoinMain() {
        id = "reddcoin.main";

        addressHeader = 61;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 30;

        name = "Reddcoin (beta)";
        symbol = "RDD";
        uriScheme = "reddcoin";
        bip44Index = 4;
        // TODO set correct values
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1000000); // DUST_HARD_LIMIT = 1000000;   // 0.01 RDD mininput
        unitExponent = 8;
//        throw new RuntimeException(name+" bip44Index " + bip44Index + "is not standardized");
    }

    private static ReddcoinMain instance = new ReddcoinMain();
    public static synchronized ReddcoinMain get() {
        return instance;
    }
}
