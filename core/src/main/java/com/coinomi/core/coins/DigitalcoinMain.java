package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author FuzzyHobbit
 */
public class DigitalcoinMain extends CoinType {
    private DigitalcoinMain() {
        id = "digitalcoin.main";

        addressHeader = 30;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 5;

        family = BitFamily.get();
        name = "Digitalcoin";
        symbol = "DGC";
        uriScheme = "digitalcoin";
        bip44Index = 18;
        unitExponent = 8;
        feePerKb = value(5000000); // 0.05 DGC
        feePolicy = FeePolicy.FLAT_FEE;
        minNonDust = value(10920);
        softDustLimit = minNonDust;
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("Digitalcoin Signed Message:\n");
    }

    private static DigitalcoinMain instance = new DigitalcoinMain();
    public static synchronized DigitalcoinMain get() {
        return instance;
    }
}
