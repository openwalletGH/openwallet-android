package com.coinomi.core.coins;

import com.coinomi.core.util.MonetaryFormat;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

/**
 * @author John L. Jegutanis
 */
public interface ValueType extends Serializable {
    public String getName();
    public String getSymbol();
    public int getUnitExponent();

    /**
     * Typical 1 coin value, like 1 Bitcoin, 1 Peercoin or 1 Dollar
     */
    public Value oneCoin();

    /**
     * Get the minimum valid amount that can be sent a.k.a. dust amount or minimum input
     */
    Value minNonDust();

    Value value(Coin coin);

    Value value(long units);

    public MonetaryFormat getMonetaryFormat();
    public MonetaryFormat getPlainFormat();

    public boolean equals(ValueType obj);

    Value value(String string);
}
