package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class NuSharesMain extends CoinType {
    private NuSharesMain() {
        id = "nushares.main";

        addressHeader = 63;
        p2shHeader = 64;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "NuShares (beta)";
        symbol = "NSR";
        uriScheme = "nushares";
        bip44Index = 11;
        feePerKb = Coin.valueOf(10000);
        minNonDust = Coin.valueOf(10000);
        unitExponent = 4;
    }

    private static NuSharesMain instance = new NuSharesMain();
    public static synchronized NuSharesMain get() {
        return instance;
    }
}
