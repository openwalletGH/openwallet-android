package com.coinomi.core.coins.families;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 *
 * Coins that belong to this family are: NXT, Burst, etc
 */
final public class NxtFamily implements CoinFamily {
    protected static NxtFamily instance = new NxtFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }

    @Override
    public String toString() {
        return "nxt";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof NxtFamily && toString().equals(obj.toString());
    }
}