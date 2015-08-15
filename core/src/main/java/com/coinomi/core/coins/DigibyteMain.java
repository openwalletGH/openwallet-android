package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author Digibyte
 */
public class DigibyteMain extends CoinType {
    private DigibyteMain() {
        id = "digibyte.main";

        addressHeader = 30;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 128;

        family = BitFamily.get();
        name = "Digibyte (beta)";
        symbol = "DGB";
        uriScheme = "digibyte";
        bip44Index = 20;
        unitExponent = 8;
        feePerKb = value(10000);
        minNonDust = value(5460);
        softDustLimit = value(100000);
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("DigiByte Signed Message:\n");
    }

    private static DigibyteMain instance = new DigibyteMain();
    public static synchronized DigibyteMain get() {
        return instance;
    }
}
