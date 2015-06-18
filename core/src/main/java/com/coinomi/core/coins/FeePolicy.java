package com.coinomi.core.coins;

/**
 * @author John L. Jegutanis
 */
public enum FeePolicy {
    // Used by almost all coins
    FEE_PER_KB,
    // Used in coins that have a flat fee
    FLAT_FEE,
}
