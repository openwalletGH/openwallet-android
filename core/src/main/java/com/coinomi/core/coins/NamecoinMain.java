package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class NamecoinMain extends BitFamily {
    private NamecoinMain() {
        id = "namecoin.main";

        addressHeader = 52;
        // Namecoin does not have p2sh addresses
        acceptableAddressCodes = new int[] { addressHeader};
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 128;

        name = "Namecoin";
        symbol = "NMC";
        uriScheme = "namecoin";
        bip44Index = 7;
        unitExponent = 8;
        feeValue = value(500000);
        minNonDust = value(10000); // 0.0001 NMC mininput
        softDustLimit = value(1000000); // 0.01 NMC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Bitcoin Signed Message:\n");
    }

    private static NamecoinMain instance = new NamecoinMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
