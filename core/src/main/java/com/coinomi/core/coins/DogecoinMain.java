package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class DogecoinMain extends CoinType {
    private DogecoinMain() {
        id = "dogecoin.main";

        addressHeader = 30;
        p2shHeader = 22;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 240; // COINBASE_MATURITY_NEW

        name = "Dogecoin";
        symbol = "DOGE";
        uriScheme = "dogecoin";
        bip44Index = 3;
        unitExponent = 8;
        feePerKb = Coin.valueOf(100000000L);
        minNonDust = Coin.valueOf(1);
        softDustLimit = Coin.valueOf(100000000L); // 1 DOGE
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static DogecoinMain instance = new DogecoinMain();
    public static synchronized DogecoinMain get() {
        return instance;
    }
}
