package com.coinomi.core.coins;

import com.coinomi.core.coins.families.PeerFamily;

/**
 * @author John L. Jegutanis
 */
public class BlackcoinMain extends PeerFamily {
    private BlackcoinMain() {
        id = "blackcoin.main";

        addressHeader = 25;
        p2shHeader = 85;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;
        dumpedPrivateKeyHeader = 153;

        name = "Blackcoin";
        symbol = "BLK";
        uriScheme = "blackcoin";
        bip44Index = 10;
        unitExponent = 8;
        feeValue = value(10000); // 0.0001 BLK
        minNonDust = value(1);
        softDustLimit = value(1000000); // 0.01 BLK
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
        signedMessageHeader = toBytes("BlackCoin Signed Message:\n");
    }

    private static BlackcoinMain instance = new BlackcoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
