package com.coinomi.core.coins;

import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class NuBitsMain extends CoinType {
    public NuBitsMain() {
        id = "nubits.main";
        uid = 121;

        addressHeader = 25;
        p2shHeader = 26;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "NuBits (beta)";
        symbol = "NBT";
        uriScheme = "nubits";
        bip44Index = 12;
        feePerKb = Coin.valueOf(1000000);
        minNonDust = Coin.valueOf(1000000);
    }

    private static NuBitsMain instance;
    public static synchronized NuBitsMain get() {
        if (instance == null) {
            instance = new NuBitsMain();
        }
        return instance;
    }
}
