package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author FuzzyHobbit
 */
public class DigitalcoinMain extends BitFamily {
    private DigitalcoinMain() {
        id = "digitalcoin.main";

        addressHeader = 30;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 5;
        dumpedPrivateKeyHeader = 158;

        name = "Digitalcoin";
        symbol = "DGC";
        uriScheme = "digitalcoin";
        bip44Index = 18;
        unitExponent = 8;
        feeValue = value(5000000); // 0.05 DGC
        feePolicy = FeePolicy.FLAT_FEE;
        minNonDust = value(10920);
        softDustLimit = minNonDust;
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("Digitalcoin Signed Message:\n");
    }

    private static DigitalcoinMain instance = new DigitalcoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
