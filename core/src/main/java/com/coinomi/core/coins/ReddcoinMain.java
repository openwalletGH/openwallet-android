package com.coinomi.core.coins;

import com.coinomi.core.coins.families.ReddFamily;

/**
 * @author John L. Jegutanis
 */
public class ReddcoinMain extends ReddFamily {
    private ReddcoinMain() {
        id = "reddcoin.main";

        addressHeader = 61;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 30;
        dumpedPrivateKeyHeader = 189;
        transactionVersion = 2;

        name = "Reddcoin";
        symbol = "RDD";
        uriScheme = "reddcoin";
        bip44Index = 4;
        unitExponent = 8;
        feeValue = value(100000);
        minNonDust = value(1000000);   // 0.01 RDD mininput
        softDustLimit = value(100000000); // 1 RDD
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Reddcoin Signed Message:\n");
    }

    private static ReddcoinMain instance = new ReddcoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
