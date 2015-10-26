package com.coinomi.core.coins.families;

/**
 * @author John L. Jegutanis
 *
 * This family contains Clams
 */
final public class ClamsFamily implements CoinFamily {
    private final static CoinFamily instance = new ClamsFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }

    @Override
    public String toString() {
        return "clams";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof ClamsFamily && toString().equals(obj.toString());
    }
}
