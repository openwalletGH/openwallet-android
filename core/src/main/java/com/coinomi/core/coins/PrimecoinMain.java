package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class PrimecoinMain extends BitFamily {
    private PrimecoinMain() {
        id = "primecoin.main";

        addressHeader = 23;
        p2shHeader = 83;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 3000;
        dumpedPrivateKeyHeader = 151;

        name = "Primecoin";
        symbol = "XPM";
        uriScheme = "primecoin";
        bip44Index = 24;
        unitExponent = 8;
        feeValue = value(1000000);
        minNonDust = value(546000);
        softDustLimit = value(1000000); // 0.01 XPM
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("Primecoin Signed Message:\n");
    }

    private static PrimecoinMain instance = new PrimecoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
