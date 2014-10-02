package com.coinomi.core.coins;

/**
 * @author Giannis Dzegoutanis
 */
public class DarkcoinMain extends CoinType {
    public DarkcoinMain() {
        id = "darkcoin.main";

        addressHeader = 76;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Darkcoin";
        symbol = "DRK";
        uriScheme = "darkcoin";
        bip44Index = 5;
    }

    private static DarkcoinMain instance;
    public static synchronized DarkcoinMain get() {
        if (instance == null) {
            instance = new DarkcoinMain();
        }
        return instance;
    }
}
