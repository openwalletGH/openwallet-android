package com.coinomi.core.coins;

import com.coinomi.core.coins.families.ClamsFamily;

/**
 * @author John L. Jegutanis
 */
public class ClamsMain extends ClamsFamily {
    private ClamsMain() {
        id = "clams.main";

        addressHeader = 137;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;
        transactionVersion = 2;
        dumpedPrivateKeyHeader = 133;

        name = "Clams";
        symbol = "CLAM";
        uriScheme = "clams";
        bip44Index = 23;
        unitExponent = 8;
        feeValue = value(10000);
        minNonDust = value(1);
        softDustLimit = value(1000000);
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("Clam Signed Message:\n");
    }

    private static ClamsMain instance = new ClamsMain();
    public static synchronized ClamsMain get() {
        return instance;
    }
}
