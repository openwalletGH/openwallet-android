package com.coinomi.core.coins;

import com.coinomi.core.Constants;

/**
 * @author Giannis Dzegoutanis
 */
public class LitecoinMain extends CoinType {
    public LitecoinMain() {
        id = Constants.ID_LITECOIN_MAIN;

        addressHeader = 48;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Litecoin";
        symbol = "LTC";
        uriScheme = "litecoin";
        bip44Index = 2;
    }

    private static LitecoinMain instance;
    public static synchronized LitecoinMain get() {
        if (instance == null) {
            instance = new LitecoinMain();
        }
        return instance;
    }
}
