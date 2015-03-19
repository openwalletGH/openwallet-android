package com.coinomi.core.coins;

import com.coinomi.core.util.MonetaryFormat;

/**
 * @author John L. Jegutanis
 */
public interface ValueType {
    public String getName();
    public String getSymbol();
    public int getUnitExponent();

    /**
     * Typical coin precision, like 1 Bitcoin or 1 Dollar
     */
    public Value oneCoin();

    public MonetaryFormat getMonetaryFormat();
    public MonetaryFormat getPlainFormat();

    public boolean equals(ValueType obj);
}
