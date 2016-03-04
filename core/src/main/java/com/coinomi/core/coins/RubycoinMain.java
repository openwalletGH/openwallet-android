package com.coinomi.core.coins;

import com.coinomi.core.coins.families.PeerFamily;

/**
 * @author FuzzyHobbit
 */
public class RubycoinMain extends PeerFamily {
    private RubycoinMain() {
        id = "rubycoin.main";

        addressHeader = 60;
        p2shHeader = 85;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 50;
        dumpedPrivateKeyHeader = 188;

        name = "Rubycoin";
        symbol = "RBY";
        uriScheme = "rubycoin";
        bip44Index = 16;
        unitExponent = 8;
        feeValue = value(10000); // 0.0001 RBY
        minNonDust = value(1);
        softDustLimit = value(1000000); // 0.01 RBY
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("Rubycoin Signed Message:\n");
    }

    private static RubycoinMain instance = new RubycoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}