package com.coinomi.core.coins;

import com.coinomi.core.Constants;

/**
 * @author Giannis Dzegoutanis
 */
public class LitecoinTest extends CoinType {
    public LitecoinTest() {
        id = Constants.ID_LITECOIN_TEST;

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Litecoin Test";
        symbol = "LTC";
        bip44Index = 3;
    }

    private static LitecoinTest instance;
    public static synchronized LitecoinTest get() {
        if (instance == null) {
            instance = new LitecoinTest();
        }
        return instance;
    }
}
