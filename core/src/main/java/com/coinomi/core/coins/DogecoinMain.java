package com.coinomi.core.coins;

import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class DogecoinMain extends CoinType {
    public DogecoinMain() {
        id = "dogecoin.main";
        uid = 31;

        addressHeader = 30;
        p2shHeader = 22;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Dogecoin";
        symbol = "DOGE";
        uriScheme = "dogecoin";
        bip44Index = 3;
        feePerKb = Coin.valueOf(100000000L);
        minNonDust = Coin.SATOSHI; // Dogecoin doesn't have dust detection (src. ref client)
    }

    private static DogecoinMain instance;
    public static synchronized DogecoinMain get() {
        if (instance == null) {
            instance = new DogecoinMain();
        }
        return instance;
    }
}
