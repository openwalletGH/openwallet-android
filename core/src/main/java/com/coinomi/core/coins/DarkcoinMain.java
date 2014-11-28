package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class DarkcoinMain extends CoinType {
    private DarkcoinMain() {
        id = "darkcoin.main";

        addressHeader = 76;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;

        name = "Darkcoin (beta)";
        symbol = "DRK";
        uriScheme = "darkcoin";
        bip44Index = 5;
        unitExponent = 8;
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1000); // 0.00001 DRK mininput
        softDustLimit = Coin.valueOf(100000); // 0.001 DRK
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static DarkcoinMain instance = new DarkcoinMain();
    public static synchronized DarkcoinMain get() {
        return instance;
    }
}
