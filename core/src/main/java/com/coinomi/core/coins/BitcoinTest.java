package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author John L. Jegutanis
 */
public class BitcoinTest extends CoinType {
    private BitcoinTest() {
        id = "bitcoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 239;

        name = "Bitcoin Test";
        symbol = "BTC";
        uriScheme = "bitcoin";
        bip44Index = 1;
        unitExponent = 8;
        feePerKb = Coin.valueOf(10000);
        minNonDust = Coin.valueOf(5460);
        softDustLimit = Coin.valueOf(1000000); // 0.01 BTC
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
    }

    private static BitcoinTest instance = new BitcoinTest();
    public static synchronized BitcoinTest get() {
        return instance;
    }
}
