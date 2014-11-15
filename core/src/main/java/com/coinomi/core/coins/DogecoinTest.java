package com.coinomi.core.coins;

import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class DogecoinTest extends CoinType {
    public DogecoinTest() {
        id = "dogecoin.test";
        uid = 32;

        addressHeader = 113;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Dogecoin Test";
        symbol = "DOGE";
        uriScheme = "dogecoin";
        bip44Index = 1;
        feePerKb = Coin.valueOf(100000000L);
        minNonDust = Coin.SATOSHI; // Dogecoin doesn't have dust detection (src. ref client)
    }

    private static DogecoinTest instance;
    public static synchronized DogecoinTest get() {
        if (instance == null) {
            instance = new DogecoinTest();
        }
        return instance;
    }
}
