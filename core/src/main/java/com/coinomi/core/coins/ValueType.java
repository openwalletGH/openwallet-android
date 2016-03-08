package com.coinomi.core.coins;

import com.coinomi.core.coins.families.Families;
import com.coinomi.core.util.MonetaryFormat;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

/**
 * @author John L. Jegutanis
 */
public interface ValueType extends Serializable {
    String getId();
    String getName();
    String getSymbol();
    int getUnitExponent();

    /**
     * Typical 1 coin value, like 1 Bitcoin, 1 Peercoin or 1 Dollar
     */
    Value oneCoin();

    /**
     * Get the minimum valid amount that can be sent a.k.a. dust amount or minimum input
     */
    Value getMinNonDust();

    Value value(Coin coin);

    Value value(long units);

    MonetaryFormat getMonetaryFormat();
    MonetaryFormat getPlainFormat();

    boolean equals(ValueType obj);

    Value value(String string);
}
