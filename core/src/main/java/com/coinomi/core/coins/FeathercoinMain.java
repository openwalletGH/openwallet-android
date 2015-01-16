package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class FeathercoinMain extends CoinType {
    private FeathercoinMain() {
        id = "feathercoin.main";

        addressHeader = 14;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 142;

        name = "Feathercoin";
        symbol = "FTC";
        uriScheme = "feathercoin";
        bip44Index = 8;
        unitExponent = 8;
        feePerKb = Coin.valueOf(2000000);
        minNonDust = Coin.valueOf(1000); // 0.00001 FTC mininput
        softDustLimit = Coin.valueOf(100000); // 0.001 FTC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static FeathercoinMain instance = new FeathercoinMain();
    public static synchronized FeathercoinMain get() {
        return instance;
    }
}
