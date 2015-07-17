package com.coinomi.core.coins.families;

/**
 * @author John L. Jegutanis
 */
final public class FiatFamily implements CoinFamily {
    private final static CoinFamily instance = new FiatFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }

    @Override
    public String toString() {
        return "fiat";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof FiatFamily && toString().equals(obj.toString());
    }
}
