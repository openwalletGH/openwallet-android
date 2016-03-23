package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author Digibyte
 */
public class DigibyteMain extends BitFamily {
    private DigibyteMain() {
        id = "digibyte.main";

        addressHeader = 30;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 128;

        name = "Digibyte (beta)";
        symbol = "DGB";
        uriScheme = "digibyte";
        bip44Index = 20;
        unitExponent = 8;
        feeValue = value(100000000);
        minNonDust = value(54600000);
        softDustLimit = value(1000000);
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("DigiByte Signed Message:\n");
    }

    private static DigibyteMain instance = new DigibyteMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
