package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class DarkcoinMain extends CoinType {
    private DarkcoinMain() {
        id = "darkcoin.main";
        uid = 51;

        addressHeader = 76;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Darkcoin (beta)";
        symbol = "DRK";
        uriScheme = "darkcoin";
        bip44Index = 5;
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1); //TODO verify
        unitExponent = 8;
    }

    private static DarkcoinMain instance = new DarkcoinMain();
    public static synchronized DarkcoinMain get() {
        return instance;
    }
}
