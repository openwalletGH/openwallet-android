package com.coinomi.core.coins;

import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class NuSharesMain extends CoinType {
    public NuSharesMain() {
        id = "nushares.main";
        uid = 111;

        addressHeader = 63;
        p2shHeader = 64;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "NuShares (beta)";
        symbol = "NSR";
        uriScheme = "nushares";
        bip44Index = 11;
        feePerKb = Coin.valueOf(100000000);
        minNonDust = Coin.valueOf(100000000);
    }

    private static NuSharesMain instance;
    public static synchronized NuSharesMain get() {
        if (instance == null) {
            instance = new NuSharesMain();
        }
        return instance;
    }
}
