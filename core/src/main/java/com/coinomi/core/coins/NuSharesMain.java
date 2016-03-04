package com.coinomi.core.coins;

import com.coinomi.core.coins.families.NuFamily;

/**
 * @author John L. Jegutanis
 */
public class NuSharesMain extends NuFamily {
    private NuSharesMain() {
        id = "nushares.main";

        addressHeader = 63;
        p2shHeader = 64;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        dumpedPrivateKeyHeader = 149;
        tokenId = 0x53;

        name = "NuShares";
        symbol = "NSR";
        uriScheme = "nu";
        bip44Index = 11;
        unitExponent = 4;
        feeValue = value(10000); // 1NSR, careful NuBits has 10000 units per coin
        minNonDust = value(10000); // 1NSR
        softDustLimit = minNonDust;
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("Nu Signed Message:\n");
    }

    private static NuSharesMain instance = new NuSharesMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
