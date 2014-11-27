package com.coinomi.core.coins;

import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class NuBitsMain extends CoinType {
    private NuBitsMain() {
        id = "nubits.main";

        addressHeader = 25;
        p2shHeader = 26;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        tokenId = 0x42;

        name = "NuBits (beta)";
        symbol = "NBT";
        uriScheme = "nubits";
        bip44Index = 12;
        feePerKb = Coin.valueOf(100); // 0.01NBT, careful NuBit has 10000 units per coin
        minNonDust = Coin.valueOf(100);
        unitExponent = 4;
    }

    private static NuBitsMain instance = new NuBitsMain();
    public static synchronized NuBitsMain get() {
        return instance;
    }
}
