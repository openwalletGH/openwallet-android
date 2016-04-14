package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author Myckel Habets / Auroracoin dev team
 */
public class AuroracoinMain extends BitFamily {
    private AuroracoinMain() {
        id = "auroracoin.main";

        addressHeader = 23;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 80;
        //dumpedPrivateKeyHeader = 176; // FIXME: do we need this?

        name = "Auroracoin (beta)";
        symbol = "AUR";
        uriScheme = "auroracoin";
        bip44Index = 85;
        unitExponent = 8;
        feeValue = value(100000);
        minNonDust = value(100000);
        softDustLimit = value(100000);
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Auroracoin Signed Message:\n");
    }

    private static AuroracoinMain instance = new AuroracoinMain();
    public static synchronized AuroracoinMain get() {
        return instance;
    }
}
