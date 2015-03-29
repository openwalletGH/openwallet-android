package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class MonacoinMain extends CoinType {
    private MonacoinMain() {
        id = "monacoin.main";

        addressHeader = 50;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;

        name = "Monacoin";
        symbol = "MONA";
        uriScheme = "monacoin";
        bip44Index = 12;
        unitExponent = 8;
        // TODO set correct values
        feePerKb = value(1);
        minNonDust = value(1000);
        throw new RuntimeException(name+" bip44Index " + bip44Index + "is not standardized");
    }

    private static MonacoinMain instance = new MonacoinMain();
    public static synchronized MonacoinMain get() {
        return instance;
    }
}
