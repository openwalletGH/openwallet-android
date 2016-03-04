package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class NeoscoinMain extends BitFamily {
    private NeoscoinMain() {
        id = "neoscoin.main";

        addressHeader = 53;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 177;

        name = "Neoscoin";
        symbol = "NEOS";
        uriScheme = "neoscoin";
        bip44Index = 25;
        unitExponent = 8;
        feeValue = value(10000);
        minNonDust = value(5460);
        softDustLimit = value(1000000); // 0.01 NEOS
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("NeosCoin Signed Message:\n");
    }

    private static NeoscoinMain instance = new NeoscoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
