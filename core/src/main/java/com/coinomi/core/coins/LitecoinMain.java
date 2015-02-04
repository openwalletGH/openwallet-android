package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class LitecoinMain extends CoinType {
    private LitecoinMain() {
        id = "litecoin.main";

        addressHeader = 48;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 176;

        name = "Litecoin";
        symbol = "LTC";
        uriScheme = "litecoin";
        bip44Index = 2;
        unitExponent = 8;
        feePerKb = Coin.valueOf(100000);
        minNonDust = Coin.valueOf(1000); // 0.00001 LTC mininput
        softDustLimit = Coin.valueOf(100000); // 0.001 LTC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static LitecoinMain instance = new LitecoinMain();
    public static synchronized LitecoinMain get() {
        return instance;
    }
}
