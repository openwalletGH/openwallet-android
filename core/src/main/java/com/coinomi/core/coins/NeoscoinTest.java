package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class NeoscoinTest extends BitFamily {
    private NeoscoinTest() {
        id = "neoscoin.test";

        addressHeader = 63;
        p2shHeader = 188;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;

        name = "Neoscoin Test";
        symbol = "NEOSt";
        uriScheme = "neoscoin";
        bip44Index = 1;
        unitExponent = 8;
        feeValue = value(10000);
        minNonDust = value(5460);
        softDustLimit = value(1000000); // 0.01 NEOS
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("NeosCoin Signed Message:\n");
    }

    private static NeoscoinTest instance = new NeoscoinTest();
    public static synchronized CoinType get() {
        return instance;
    }
}
