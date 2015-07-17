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

    @Override
    public String toString() {
        return "bitcoin";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof BitFamily && toString().equals(obj.toString());
    }
}
