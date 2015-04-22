package com.coinomi.core.util;

/**
 * Copyright 2014 Andreas Schildbach
 * Copyright 2015 John L. Jegutanis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.bitcoinj.core.Coin;

/**
 * An exchange rate is expressed as a ratio of a pair of {@link Value} amounts.
 */
public class ExchangeRateBase implements ExchangeRate {
    private static final int RATE_SCALE = 10;
    public static final String ZERO_RATE_ERROR_MESSAGE = "Exchange rate cannot be zero";

    public final Value value1;
    public final Value value2;

    /** Construct exchange rate. This amount of coin is worth that amount of fiat. */
    public ExchangeRateBase(Value value1, Value value2) {
        this.value1 = checkNonZero(value1);
        this.value2 = checkNonZero(value2);
    }

    public ExchangeRateBase(ValueType type1, ValueType type2, String rateString) {
        // Make the rate having maximum of RATE_SCALE decimal places
        BigDecimal rate = new BigDecimal(rateString)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        checkState(rate.signum() >= 0); // rate cannot be negative
        // Check if the rate has too many decimal places or scale() for the type2 to handle.
        if (rate.scale() > type2.getUnitExponent()) {
            // If we have too many decimal places, multiply everything by a factor so that the rate
            // can fit in a type2 value. For example if the rate is 0.123456789 but a type2 can only
            // handle 4 places, then final result will be value1 = 100000 and value2 = 12345.6789
            BigDecimal rateFactor = BigDecimal.TEN.pow(rate.scale() - type2.getUnitExponent());
            value1 = type1.oneCoin().multiply(rateFactor.longValue());
            value2 = Value.parse(type2, rate.multiply(rateFactor));
        } else {
            value1 = type1.oneCoin();
            value2 = Value.parse(type2, rate);
        }

        checkNonZero(value1);
        checkNonZero(value2);
    }

    private static Value checkNonZero(Value value) {
        checkArgument(!value.isZero(), ZERO_RATE_ERROR_MESSAGE);
        return value;
    }

    @Override
    public Value convert(CoinType type, Coin coin) {
        return convertValue(type.value(coin));
    }

    @Override
    public Value convert(Value convertingValue) {
        return convertValue(convertingValue);
    }

    @Override
    public ValueType getOtherType(ValueType type) {
        checkIfValueTypeAvailable(type);
        if (value1.type.equals(type)) {
            return value2.type;
        } else {
            return value1.type;
        }
    }

    @Override
    public ValueType getSourceType() {
        return value1.type;
    }

    @Override
    public ValueType getDestinationType() {
        return value2.type;
    }

    @Override
    public boolean canConvert(ValueType type1, ValueType type2) {
        try {
            checkIfValueTypeAvailable(type1);
            checkIfValueTypeAvailable(type2);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    protected Value convertValue(Value convertingValue) {
        checkIfValueTypeAvailable(convertingValue.type);

        Value rateFrom = getFromRateValue(convertingValue.type);
        Value rateTo = getToRateValue(convertingValue.type);

        // Use BigDecimal because it's much easier to maintain full precision without overflowing.
        final BigDecimal converted = BigDecimal.valueOf(convertingValue.value)
                .multiply(BigDecimal.valueOf(rateTo.value))
                .divide(BigDecimal.valueOf(rateFrom.value), RoundingMode.HALF_UP);
        if (converted.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                || converted.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0)
            throw new ArithmeticException("Overflow");
        return Value.valueOf(rateTo.type, converted.longValue());
    }

    protected Value getFromRateValue(ValueType fromType) {
        if (value1.type.equals(fromType)) {
            return value1;
        } else if (value2.type.equals(fromType)) {
            return value2;
        } else {
            // Should not happen
            throw new IllegalStateException("Could not get 'from' rate");
        }
    }

    protected Value getToRateValue(ValueType fromType) {
        if (value1.type.equals(fromType)) {
            return value2;
        } else if (value2.type.equals(fromType)) {
            return value1;
        } else {
            // Should not happen
            throw new IllegalStateException("Could not get 'to' rate");
        }
    }

    protected void checkIfValueTypeAvailable(ValueType type) {
        checkArgument(value1.type.equals(type) || value2.type.equals(type),
                "This exchange rate does not apply to: %s", type.getSymbol());
    }
}
