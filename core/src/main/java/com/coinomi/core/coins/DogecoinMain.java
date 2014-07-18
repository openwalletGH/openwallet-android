package com.coinomi.core.coins;

import com.coinomi.core.Constants;

/**
 * @author Giannis Dzegoutanis
 */
public class DogecoinMain extends CoinType {
    public DogecoinMain() {
        id = Constants.ID_DOGECOIN_MAIN;

        addressHeader = 30;
        p2shHeader = 22;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Dogecoin";
        symbol = "DOGE";
        bip44Index = 4;
    }

    private static DogecoinMain instance;
    public static synchronized DogecoinMain get() {
        if (instance == null) {
            instance = new DogecoinMain();
        }
        return instance;
    }
}
