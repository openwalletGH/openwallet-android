package com.coinomi.core.coins.families;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 *
 * Coins that belong to this family are: NXT, Burst, etc
 */
final public class NxtFamily implements CoinFamily {
    protected static NxtFamily instance= new NxtFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }
}