package com.coinomi.core.coins;

import com.coinomi.core.coins.families.BitFamily;

/**
 * @author John L. Jegutanis
 */
public class DashMain extends BitFamily {
    private DashMain() {
        id = "dash.main"; // Do not change this id as wallets serialize this string

        addressHeader = 76;
        p2shHeader = 16;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 204;

        name = "Dash";
        symbol = "DASH";
        uriScheme = "dash"; // TODO add multi uri, darkcoin
        bip44Index = 5;
        unitExponent = 8;
        feeValue = value(100000);
        minNonDust = value(1000); // 0.00001 DASH mininput
        softDustLimit = value(100000); // 0.001 DASH
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
        signedMessageHeader = toBytes("DarkCoin Signed Message:\n");
    }

    private static DashMain instance = new DashMain();
    public static synchronized CoinType get() {
        return instance;
    }
}
