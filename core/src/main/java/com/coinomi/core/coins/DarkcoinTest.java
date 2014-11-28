package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class DarkcoinTest extends CoinType {
    private DarkcoinTest() {
        id = "darkcoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;

        name = "Darkcoin Test";
        symbol = "DRK";
        uriScheme = "darkcoin";
        bip44Index = 1;
        unitExponent = 8;
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1000); // 0.00001 DRK mininput
        softDustLimit = Coin.valueOf(100000); // 0.001 DRK
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static DarkcoinTest instance = new DarkcoinTest();
    public static synchronized DarkcoinTest get() {
        return instance;
    }
}
