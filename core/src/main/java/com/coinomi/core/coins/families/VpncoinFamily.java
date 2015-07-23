package com.coinomi.core.coins.families;

/**
 * @author John L. Jegutanis
 *
 * This family contains Vpncoin
 */
final public class VpncoinFamily implements CoinFamily {
    private final static CoinFamily instance = new VpncoinFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }

    @Override
    public String toString() {
        return "vpncoin";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof VpncoinFamily && toString().equals(obj.toString());
    }
}
