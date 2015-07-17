package com.coinomi.core.coins.families;

/**
 * @author John L. Jegutanis
 *
 * This family contains Peercoin, Blackcoin, etc
 */
final public class PeerFamily implements CoinFamily {
    private final static CoinFamily instance = new PeerFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }

    @Override
    public String toString() {
        return "peercoin";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof PeerFamily && toString().equals(obj.toString());
    }
}
