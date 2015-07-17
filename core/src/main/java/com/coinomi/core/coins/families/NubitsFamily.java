package com.coinomi.core.coins.families;

/**
 * @author John L. Jegutanis
 *
 * This family contains NuBits, NuShares, BlockShares, etc
 */
final public class NubitsFamily implements CoinFamily {
    private final static CoinFamily instance = new NubitsFamily();
    public static synchronized CoinFamily get() {
        return instance;
    }

    @Override
    public String toString() {
        return "nubits";
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof NubitsFamily && toString().equals(obj.toString());
    }
}
