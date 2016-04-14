package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author Malcolm MacLeod
 */
public class GuldenMain extends BitFamily {
    private GuldenMain() {
        id = "gulden.main";

        addressHeader = 38;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 166;

        name = "Gulden (beta)";
        symbol = "NLG";
        uriScheme = "gulden";
        bip44Index = 87;
        unitExponent = 8;
        feeValue = value(1000000);
        minNonDust = value(10000);
        softDustLimit = value(100000);
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Gulden Signed Message:\n");
    }

    private static GuldenMain instance = new GuldenMain();
    public static synchronized GuldenMain get() {
        return instance;
    }
}
