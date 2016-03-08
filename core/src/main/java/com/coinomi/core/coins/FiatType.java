package com.coinomi.core.coins;

import com.coinomi.core.coins.families.Families;
import com.coinomi.core.util.Currencies;
import com.coinomi.core.util.MonetaryFormat;

import org.bitcoinj.core.Coin;

import java.math.BigInteger;
import java.util.HashMap;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public class FiatType implements ValueType {
    public static final int SMALLEST_UNIT_EXPONENT = 8;

    public static final MonetaryFormat PLAIN_FORMAT = new MonetaryFormat().noCode()
            .minDecimals(0).repeatOptionalDecimals(1, SMALLEST_UNIT_EXPONENT);

    public static final MonetaryFormat FRIENDLY_FORMAT = new MonetaryFormat().noCode()
            .minDecimals(2).optionalDecimals(2, 2, 2).postfixCode();

    private static final HashMap<String, FiatType> types = new HashMap<>();

    private final String name;
    private final String currencyCode;
    private transient Value oneCoin;
    private transient MonetaryFormat friendlyFormat;

    public FiatType(final String currencyCode, @Nullable final String name) {
        this.name = name != null ? name : "";
        this.currencyCode = currencyCode;
    }

    public static FiatType get(String currencyCode) {
        if (!types.containsKey(currencyCode)) {
            types.put(currencyCode, new FiatType(currencyCode, Currencies.CURRENCY_NAMES.get(currencyCode)));
        }
        return types.get(currencyCode);
    }

    @Override
    public String getId() {
        return getSymbol();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSymbol() {
        return currencyCode;
    }

    @Override
    public int getUnitExponent() {
        return SMALLEST_UNIT_EXPONENT;
    }

    @Override
    public Value oneCoin() {
        if (oneCoin == null) {
            BigInteger units = BigInteger.TEN.pow(getUnitExponent());
            oneCoin = Value.valueOf(this, units.longValue());
        }
        return oneCoin;
    }

    @Override
    public Value getMinNonDust() {
        return value(1);
    }

    @Override
    public Value value(Coin coin) {
        return Value.valueOf(this, coin);
    }

    @Override
    public Value value(long units) {
        return Value.valueOf(this, units);
    }

    @Override
    public Value value(String string) {
        return Value.parse(this, string);
    }

    @Override
    public MonetaryFormat getMonetaryFormat() {
        if (friendlyFormat == null) {
            friendlyFormat = FRIENDLY_FORMAT.code(0, currencyCode);
        }
        return friendlyFormat;
    }

    @Override
    public MonetaryFormat getPlainFormat() {
        return PLAIN_FORMAT;
    }

    @Override
    public boolean equals(ValueType o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return currencyCode.equals(o.getSymbol());
    }

    @Override
    public String toString() {
        return "Fiat {" +
                "name='" + name + '\'' +
                ", currencyCode='" + currencyCode +
                '}';
    }
}
