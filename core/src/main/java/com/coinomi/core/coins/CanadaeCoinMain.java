package com.coinomi.core.coins;

/**
 * @author John L. Jegutanis
 */
public class CanadaeCoinMain extends CoinType {
    private CanadaeCoinMain() {
        id = "CanadaeCoin.main";

        addressHeader = 28;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 120; // COINBASE_MATURITY_NEW

        name = "Canada eCoin (beta)";
        symbol = "CDN";
        uriScheme = "canadaecoin";
        bip44Index = 34;
        unitExponent = 8;
        feePerKb = value(100000);
        minNonDust = value(100000);
        softDustLimit = value(100000); // 0.001 CDN
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static CanadaeCoinMain instance = new CanadaeCoinMain();
    public static synchronized CanadaeCoinMain get() {
        return instance;
    }
}
