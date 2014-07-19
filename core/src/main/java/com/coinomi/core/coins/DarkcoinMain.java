package com.coinomi.core.coins;

import com.coinomi.core.Constants;

/**
 * @author Giannis Dzegoutanis
 */
public class DarkcoinMain extends CoinType {
    public DarkcoinMain() {
        id = Constants.ID_DARKCOIN_MAIN;

        addressHeader = 76;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Darkcoin";
        symbol = "DRK";
        uriScheme = "darkcoin";
        bip44Index = 8;
    }

    private static DarkcoinMain instance;
    public static synchronized DarkcoinMain get() {
        if (instance == null) {
            instance = new DarkcoinMain();
        }
        return instance;
    }
}
