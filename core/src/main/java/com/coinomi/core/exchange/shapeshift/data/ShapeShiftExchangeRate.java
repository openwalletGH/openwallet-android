package com.coinomi.core.exchange.shapeshift.data;

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
import com.coinomi.core.util.ExchangeRateBase;

import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * An exchange rate is expressed as a ratio of a pair of {@link com.coinomi.core.coins.Value} amounts.
 */
public class ShapeShiftExchangeRate extends ExchangeRateBase {
    public final Value minerFee;

    public ShapeShiftExchangeRate(Value deposit, Value withdraw, Value minerFee) {
        super(deposit, withdraw);
        if (minerFee != null) checkArgument(withdraw.type.equals(minerFee.type));
        this.minerFee = minerFee;
    }

    public ShapeShiftExchangeRate(ValueType depositType, ValueType withdrawType, String rateString,
                                  String minerFeeString) {
        super(depositType, withdrawType, rateString);

        if (minerFeeString != null) {
            minerFee = withdrawType.value(minerFeeString);
        } else {
            minerFee = null;
        }
    }

    @Override
    public Value convert(CoinType type, Coin coin) {
        return convert(Value.valueOf(type, coin));
    }

    @Override
    public Value convert(Value convertingValue) {
        Value converted = convertValue(convertingValue);
        if (!converted.isZero() && minerFee != null) {
            Value fee;
            // Deposit -> withdrawal
            if (converted.type.equals(minerFee.type)) {
                fee = minerFee.negate(); // Miner fee is removed from withdraw value
            } else { // Withdrawal -> deposit
                fee = convertValue(minerFee); // Miner fee is added to the deposit value
            }
            converted = converted.add(fee);

            // If the miner fee is higher than the value we are converting we get 0
            if (converted.isNegative()) converted = converted.multiply(0);
        }
        return converted;
    }

}
