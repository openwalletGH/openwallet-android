package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class BlackcoinMain extends CoinType {
    private BlackcoinMain() {
        id = "blackcoin.main";

        addressHeader = 25;
        p2shHeader = 85;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 500;

        name = "Blackcoin";
        symbol = "BLK";
        uriScheme = "blackcoin";
        bip44Index = 10;
        unitExponent = 8;
        feePerKb = value(10000); // 0.0001 BLK
        minNonDust = value(1);
        softDustLimit = value(1000000); // 0.01 BLK
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
    }

    private static BlackcoinMain instance = new BlackcoinMain();
    public static synchronized BlackcoinMain get() {
        return instance;
    }
}
