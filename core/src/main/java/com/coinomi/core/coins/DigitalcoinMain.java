package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author FuzzyHobbit
 */
public class DigitalcoinMain extends CoinType {
    private DigitalcoinMain() {
        id = "digitalcoin.main";

        addressHeader = 30;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 5;

        name = "Digitalcoin";
        symbol = "DGC";
        uriScheme = "digitalcoin";
        bip44Index = 18;
        unitExponent = 8;
        feePerKb = Coin.valueOf(10000); // 0.0001 DGC
        minNonDust = Coin.valueOf(1);
        softDustLimit = Coin.valueOf(1000000); // 0.01 DGC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static DigitalcoinMain instance = new DigitalcoinMain();
    public static synchronized DigitalcoinMain get() {
        return instance;
    }
}
