package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class DogecoinTest extends CoinType {
    private DogecoinTest() {
        id = "dogecoin.test";

        addressHeader = 113;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 240; // COINBASE_MATURITY_NEW

        name = "Dogecoin Test";
        symbol = "DOGE";
        uriScheme = "dogecoin";
        bip44Index = 1;
        feePerKb = Coin.valueOf(100000000L);
        minNonDust = Coin.SATOSHI; // Dogecoin doesn't have dust detection (src. ref client)
        unitExponent = 8;
    }

    private static DogecoinTest instance = new DogecoinTest();
    public static synchronized DogecoinTest get() {
        return instance;
    }
}
