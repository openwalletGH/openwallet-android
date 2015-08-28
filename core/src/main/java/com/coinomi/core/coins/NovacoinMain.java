package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;
import com.coinomi.core.coins.families.PeerFamily;

public class NovacoinMain extends CoinType {
    private NovacoinMain() {
        id = "novacoin.main";

        addressHeader = 8;
        p2shHeader = 20;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 520;

        family = PeerFamily.get();
        name = "Novacoin (beta)";
        symbol = "NVC";
        uriScheme = "novacoin";
        bip44Index = 50;
        unitExponent = 6;
        feePerKb = value(1000); // 0.001 NVC
        minNonDust = value(1);
        softDustLimit = value(10000); // 0.01 NVC
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static NovacoinMain instance = new NovacoinMain();
    public static synchronized NovacoinMain get() {
        return instance;
    }
}
