package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

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

        name = "Digibyte (beta)";
        symbol = "DGB";
        uriScheme = "digibyte";
        bip44Index = 20;
        unitExponent = 8;
        feePerKb = Coin.valueOf(10000);
        minNonDust = Coin.valueOf(5460);
        softDustLimit = Coin.valueOf(100000);
        softDustPolicy = SoftDustPolicy.BASE_FEE_FOR_EACH_SOFT_DUST_TXO;
    }

    private static DigibyteMain instance = new DigibyteMain();
    public static synchronized DigibyteMain get() {
        return instance;
    }
}
