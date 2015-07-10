package com.coinomi.core.coins;

import com.coinomi.core.coins.families.NxtFamily;

/**
 * @author John L. Jegutanis
 */
public class BurstMain extends CoinType {

    private BurstMain() {
        id = "burst.main";

        family = NxtFamily.get();
        name = "Burst";
        symbol = "BURST";
        uriScheme = "burst";
        bip44Index = 30;
        unitExponent = 8;
        addressPrefix = "BURST-";
    }

    private static BurstMain instance = new BurstMain();
    public static synchronized BurstMain get() {
        return instance;
    }
}
