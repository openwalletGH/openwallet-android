package com.coinomi.core.coins.families;

/**
 * @author John L. Jegutanis
 *
 * This family contains Reddcoin, Cannacoin, etc
 */
final public class ReddFamily implements CoinFamily {
    private final static CoinFamily instance = new ReddFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }

    @Override
    public String toString() {
        return "reddcoin";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof ReddFamily && toString().equals(obj.toString());
    }
}
