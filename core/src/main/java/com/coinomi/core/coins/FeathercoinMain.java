package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class FeathercoinMain extends BitFamily {
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
        feeValue = value(2000000);
        minNonDust = value(1000); // 0.00001 FTC mininput
        softDustLimit = value(100000); // 0.001 FTC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Feathercoin Signed Message:\n");
    }

    private static FeathercoinMain instance = new FeathercoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
