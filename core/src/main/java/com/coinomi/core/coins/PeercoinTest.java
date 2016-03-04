package com.coinomi.core.coins;

import com.coinomi.core.coins.families.PeerFamily;

/**
 * @author John L. Jegutanis
 */
public class PeercoinTest extends PeerFamily {
    private PeercoinTest() {
        id = "peercoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;
        dumpedPrivateKeyHeader = 239;

        name = "Peercoin Test";
        symbol = "PPCt";
        uriScheme = "peercoin"; // TODO verify, could be ppcoin?
        bip44Index = 1;
        unitExponent = 6;
        feeValue = value(10000); // 0.01PPC, careful Peercoin has 1000000 units per coin
        minNonDust = value(10000); // 0.01PPC
        softDustLimit = minNonDust;
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("PPCoin Signed Message:\n");
    }

    private static PeercoinTest instance = new PeercoinTest();
    public static synchronized CoinType get() {
        return instance;
    }
}
