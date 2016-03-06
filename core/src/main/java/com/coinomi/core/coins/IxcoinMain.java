package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author Ahmed Bodiwala
 */
public class IxcoinMain extends BitFamily {
    private IxcoinMain() {
        id = "ixcoin.main";

        addressHeader = 138;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 120; // COINBASE_MATURITY_NEW

        name = "IXCoin";
        symbol = "IXC";
        uriScheme = "ixcoin";
        bip44Index = 86;
        unitExponent = 8;
        feeValue = value(10000);
        minNonDust = value(1);
        softDustLimit = value(1000000); // 0.01 IXC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("Ixcoin Signed Message:\n");
    }

    private static IxcoinMain instance = new IxcoinMain();
    public static synchronized IxcoinMain get() {
        return instance;
    }
}
