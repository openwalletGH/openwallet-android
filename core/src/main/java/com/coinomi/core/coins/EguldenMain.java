package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

public class EguldenMain extends BitFamily {
    private EguldenMain() {
        id = "egulden.main";

        addressHeader = 48;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 176;

        name = "e-Gulden (beta)";
        symbol = "EFL";
        uriScheme = "egulden";
        bip44Index = 2;
        unitExponent = 8;
        feeValue = value(100000);
        minNonDust = value(1000); // 0.00001 EFL mininput
        softDustLimit = value(100000); // 0.001 EFL
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("e-Gulden Signed Message:\n");
    }

    private static EguldenMain instance = new EguldenMain();
    public static synchronized EguldenMain get() {
        return instance;
    }
}
