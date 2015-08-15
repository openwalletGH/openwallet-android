package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class DogecoinTest extends CoinType {
    private DogecoinTest() {
        id = "dogecoin.test";

        addressHeader = 113;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 240; // COINBASE_MATURITY_NEW

        family = BitFamily.get();
        name = "Dogecoin Test";
        symbol = "DOGEt";
        uriScheme = "dogecoin";
        bip44Index = 1;
        unitExponent = 8;
        feePerKb = value(100000000L);
        minNonDust = value(1);
        softDustLimit = value(100000000L); // 1 DOGE
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Dogecoin Signed Message:\n");
    }

    private static DogecoinTest instance = new DogecoinTest();
    public static synchronized DogecoinTest get() {
        return instance;
    }
}
