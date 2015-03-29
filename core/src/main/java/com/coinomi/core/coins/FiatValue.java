package com.coinomi.core.coins;

import org.bitcoinj.utils.Fiat;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author John L. Jegutanis
 */
public class FiatValue {
    public static Value valueOf(final String currencyCode, final long units) {
        return Value.valueOf(FiatType.get(currencyCode), units);
    }

    /**
     * Convert an amount expressed in the way humans are used to into units.
     */
    public static Value valueOf(final String currencyCode, final int coins, final int cents) {
        return Value.valueOf(FiatType.get(currencyCode), coins, cents);
    }

    public static Value valueOf(final Fiat fiat) {
        return FiatValue.valueOf(fiat.currencyCode, fiat.getValue());
    }

    /**
     * Parses an amount expressed in the way humans are used to.<p>
     * <p/>
     * This takes string in a format understood by {@link java.math.BigDecimal#BigDecimal(String)},
     * for example "0", "1", "0.10", "1.23E3", "1234.5E-5".
     *
     * @throws IllegalArgumentException if you try to specify fractional units, or a value out of range.
     */
    public static Value parse(final String currencyCode, final String str) {
        return Value.parse(FiatType.get(currencyCode), str);
    }
}
