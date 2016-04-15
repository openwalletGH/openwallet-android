package com.coinomi.core.coins;

import com.coinomi.core.coins.families.PeerFamily;

/**
 * @author John L. Jegutanis
 */
public class ClubcoinMain extends PeerFamily {
    private ClubcoinMain() {
        id = "clubcoin.main";

        addressHeader = 28;
        p2shHeader = 85;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;
        dumpedPrivateKeyHeader = 153;

        name = "Clubcoin (beta)";
        symbol = "CLUB";
        uriScheme = "clubcoin";
        bip44Index = 79;
        unitExponent = 8;
        feeValue = value(10000);
        minNonDust = value(1);
        softDustLimit = value(1);
        softDustPolicy = SoftDustPolicy.NO_POLICY;
        signedMessageHeader = toBytes("ClubCoin Signed Message:\n");
    }

    private static ClubcoinMain instance = new ClubcoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
