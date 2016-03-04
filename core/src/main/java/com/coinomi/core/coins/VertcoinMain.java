package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class VertcoinMain extends BitFamily {
    private VertcoinMain() {
        id = "vertcoin.main";

        addressHeader = 71;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 199;

        name = "Vertcoin";
        symbol = "VTC";
        uriScheme = "vertcoin";
        bip44Index = 28;
        unitExponent = 8;
        feeValue = value(100000);
        minNonDust = value(1000); // 0.00001 VTC mininput
        softDustLimit = value(100000); // 0.001 VTC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Vertcoin Signed Message:\n");
    }

    private static VertcoinMain instance = new VertcoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
