package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class MonacoinMain extends BitFamily {
    private MonacoinMain() {
        id = "monacoin.main";

        addressHeader = 50;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 178;

        name = "Monacoin";
        symbol = "MONA";
        uriScheme = "monacoin";
        bip44Index = 22;
        unitExponent = 8;
        feeValue = value(100000);
        minNonDust = value(1000); // 0.00001 MNC mininput
        softDustLimit = value(100000); // 0.001 MONA
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Monacoin Signed Message:\n");
    }

    private static MonacoinMain instance = new MonacoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
