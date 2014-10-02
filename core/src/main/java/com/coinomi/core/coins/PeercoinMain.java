package com.coinomi.core.coins;

/**
 * @author Giannis Dzegoutanis
 */
public class PeercoinMain extends CoinType {
    public PeercoinMain() {
        id = "peercoin.main";

        addressHeader = 55;
        p2shHeader = 117;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        name = "Peercoin";
        symbol = "PPC";
        uriScheme = "peercoin"; // TODO verify, could be ppcoin?
        bip44Index = 4;
    }

    private static PeercoinMain instance;
    public static synchronized PeercoinMain get() {
        if (instance == null) {
            instance = new PeercoinMain();
        }
        return instance;
    }
}
