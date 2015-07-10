package com.coinomi.core.coins.families;

/**
 * @author John L. Jegutanis
 *
 * This is the classical Bitcoin family that includes Litecoin, Dogecoin, Dash, etc
 */
final public class BitFamily implements CoinFamily {
    private final static CoinFamily instance = new BitFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }
}
