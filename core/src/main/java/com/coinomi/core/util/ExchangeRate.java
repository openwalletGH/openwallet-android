package com.coinomi.core.util;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

/**
 * @author John L. Jegutanis
 */
public interface ExchangeRate extends Serializable {
    @Deprecated
    Value convert(CoinType type, Coin coin);

    /**
     * Convert from one value to another
     */
    Value convert(Value convertingValue);

    ValueType getOtherType(ValueType type);

    ValueType getSourceType();
    ValueType getDestinationType();

    boolean canConvert(ValueType type1, ValueType type2);
}
