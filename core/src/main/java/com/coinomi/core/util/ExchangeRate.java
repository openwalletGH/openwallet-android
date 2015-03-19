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

import java.io.Serializable;
import java.math.BigInteger;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

/**
 * An exchange rate is expressed as a ratio of a {@link Coin} and a {@link Fiat} amount.
 */
public class ExchangeRate implements Serializable {

    public final Value value1;
    public final Value value2;

    /** Construct exchange rate. This amount of coin is worth that amount of fiat. */
    public ExchangeRate(Value value1, Value value2) {
        this.value1 = value1;
        this.value2 = value2;
    }

    public Value convert(CoinType type, Coin coin) {
        return convert(Value.valueOf(type, coin));
    }

    /**
     * Convert from one value to another
     */
    public Value convert(Value convertingValue) {
        checkIfValueTypeAvailable(convertingValue.type);

        Value rateFrom = getFromRateValue(convertingValue.type);
        Value rateTo = getToRateValue(convertingValue.type);

        // Use BigInteger because it's much easier to maintain full precision without overflowing.
        final BigInteger converted = BigInteger.valueOf(convertingValue.value)
                .multiply(BigInteger.valueOf(rateTo.value))
                .divide(BigInteger.valueOf(rateFrom.value));
        if (converted.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                || converted.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0)
            throw new ArithmeticException("Overflow");
        return Value.valueOf(rateTo.type, converted.longValue());
    }

    public ValueType getOtherType(ValueType type) {
        checkIfValueTypeAvailable(type);
        if (value1.type.equals(type)) {
            return value2.type;
        } else {
            return value1.type;
        }
    }

    private Value getFromRateValue(ValueType fromType) {
        if (value1.type.equals(fromType)) {
            return value1;
        } else if (value2.type.equals(fromType)) {
            return value2;
        } else {
            // Should not happen
            throw new IllegalStateException("Could not get 'from' rate");
        }
    }

    private Value getToRateValue(ValueType fromType) {
        if (value1.type.equals(fromType)) {
            return value2;
        } else if (value2.type.equals(fromType)) {
            return value1;
        } else {
            // Should not happen
            throw new IllegalStateException("Could not get 'to' rate");
        }
    }

    private void checkIfValueTypeAvailable(ValueType type) {
        checkArgument(value1.type.equals(type) || value2.type.equals(type),
                "This exchange rate does not apply to: %s", type.getSymbol());
    }
}
