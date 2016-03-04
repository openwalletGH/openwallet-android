package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class DogecoinTest extends BitFamily {
    private DogecoinTest() {
        id = "dogecoin.test";

        addressHeader = 113;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 240; // COINBASE_MATURITY_NEW
        dumpedPrivateKeyHeader = 241;

        name = "Dogecoin Test";
        symbol = "DOGEt";
        uriScheme = "dogecoin";
        bip44Index = 1;
        unitExponent = 8;
        feeValue = value(100000000L);
        minNonDust = value(1);
        softDustLimit = value(100000000L); // 1 DOGE
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Dogecoin Signed Message:\n");
    }

    private static DogecoinTest instance = new DogecoinTest();
    public static synchronized CoinType get() {
        return instance;
    }
}
