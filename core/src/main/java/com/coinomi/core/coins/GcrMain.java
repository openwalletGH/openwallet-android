package com.coinomi.core.coins;

import com.coinomi.core.coins.families.PeerFamily;

public class GcrMain extends CoinType {
    private GcrMain() {
        id = "gcr.main";

        addressHeader = 38;
        p2shHeader = 97;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 30;

        family = PeerFamily.get();
        name = "GCRCoin (beta)";
        symbol = "GCR";
        uriScheme = "gcr";
        bip44Index = 49;
        unitExponent = 8;
        feePerKb = value(10000);
        minNonDust = value(1);
        softDustLimit = value(10000);
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("GCR Signed Message:\n");
    }

    private static GcrMain instance = new GcrMain();
    public static synchronized GcrMain get() {
        return instance;
    }
}
