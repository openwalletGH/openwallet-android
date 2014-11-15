package com.coinomi.core.coins;

import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class PeercoinMain extends CoinType {
    public PeercoinMain() {
        id = "peercoin.main";
        uid = 41;

        addressHeader = 55;
        p2shHeader = 117;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Peercoin (beta)";
        symbol = "PPC";
        uriScheme = "peercoin"; // TODO verify, could be ppcoin?
        bip44Index = 4;
        feePerKb = Coin.valueOf(1000000); // 0.01PPC
        minNonDust = Coin.valueOf(1); //TODO verify
    }

    private static PeercoinMain instance;
    public static synchronized PeercoinMain get() {
        if (instance == null) {
            instance = new PeercoinMain();
        }
        return instance;
    }
}
