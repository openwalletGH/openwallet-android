package com.coinomi.core.coins;

import com.coinomi.core.coins.families.NxtFamily;

/**
 * @author John L. Jegutanis
 */
public class NxtMain extends CoinType {

    private NxtMain() {
        id = "nxt.main";

        family = NxtFamily.get();
        name = "NXT";
        symbol = "NXT";
        uriScheme = "nxt";
        bip44Index = 29;
        unitExponent = 8;
        addressPrefix = "NXT-";
    }

    private static NxtMain instance = new NxtMain();
    public static synchronized NxtMain get() {
        return instance;
    }
}
