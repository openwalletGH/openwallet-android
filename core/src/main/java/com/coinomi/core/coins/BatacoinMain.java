package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class BatacoinMain extends BitFamily {
    private BatacoinMain() {
        id = "bata.main";

        addressHeader = 25;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 153;

        name = "Bata (beta)";
        symbol = "BTA";
        uriScheme = "bata";
        bip44Index = 89;
        unitExponent = 8;
        feeValue = value(1000);
        minNonDust = value(1000); // 0.00001 LTC mininput
        softDustLimit = value(1000); // 0.001 LTC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static BatacoinMain instance = new BatacoinMain();
    public static synchronized BatacoinMain get() {
        return instance;
    }
}
