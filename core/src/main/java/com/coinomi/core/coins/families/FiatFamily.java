package com.coinomi.core.coins.families;

/**
 * @author John L. Jegutanis
 */
public class FiatFamily implements CoinFamily {
    private final static CoinFamily instance = new FiatFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }
}
