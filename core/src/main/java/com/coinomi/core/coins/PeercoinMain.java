package com.coinomi.core.coins;

import com.coinomi.core.coins.families.PeerFamily;

/**
 * @author John L. Jegutanis
 */
public class PeercoinMain extends PeerFamily {
    private PeercoinMain() {
        id = "peercoin.main";

        addressHeader = 55;
        p2shHeader = 117;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;
        dumpedPrivateKeyHeader = 183;

        name = "Peercoin";
        symbol = "PPC";
        uriScheme = "peercoin"; // TODO verify, could be ppcoin?
        bip44Index = 6;
        unitExponent = 6;
        feeValue = value(10000); // 0.01PPC, careful Peercoin has 1000000 units per coin
        minNonDust = value(10000); // 0.01PPC
        softDustLimit = minNonDust;
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("PPCoin Signed Message:\n");
    }

    private static PeercoinMain instance = new PeercoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
