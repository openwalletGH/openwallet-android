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
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1000); // DUST_HARD_LIMIT = 1000;   // 0.00001 DRK mininput
        unitExponent = 8;
    }

    private static DarkcoinMain instance = new DarkcoinMain();
    public static synchronized DarkcoinMain get() {
        return instance;
    }
}
