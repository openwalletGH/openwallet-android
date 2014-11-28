package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class BitcoinMain extends CoinType {
    private BitcoinMain() {
        id = "bitcoin.main";

        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        spendableCoinbaseDepth = 100;
        dumpedPrivateKeyHeader = 128;

        name = "Bitcoin";
        symbol = "BTC";
        uriScheme = "bitcoin";
        bip44Index = 0;
        unitExponent = 8;
        feePerKb = Coin.valueOf(1000);
        minNonDust = Coin.valueOf(546);
        softDustLimit = Coin.valueOf(1000000); // 0.01 BTC
        softDustPolicy = SoftDustPolicy.AT_LEAST_BASE_FEE_IF_SOFT_DUST_TXO_PRESENT;
    }

    private static BitcoinMain instance = new BitcoinMain();
    public static synchronized BitcoinMain get() {
        return instance;
    }
}
