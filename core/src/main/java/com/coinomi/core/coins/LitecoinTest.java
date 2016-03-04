package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class LitecoinTest extends BitFamily {
    private LitecoinTest() {
        id = "litecoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 239;

        name = "Litecoin Test";
        symbol = "LTCt";
        uriScheme = "litecoin";
        bip44Index = 1;
        unitExponent = 8;
        feeValue = value(100000);
        minNonDust = value(1000); // 0.00001 LTC mininput
        softDustLimit = value(100000); // 0.001 LTC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Litecoin Signed Message:\n");
    }

    private static LitecoinTest instance = new LitecoinTest();
    public static synchronized CoinType get() {
        return instance;
    }
}
