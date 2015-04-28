package com.coinomi.core.coins;

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


import com.coinomi.core.util.GenericUtils;

import static com.coinomi.core.coins.Value.*;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.bitcoinj.core.Coin;
import org.junit.Test;

import java.math.BigDecimal;

public class ValueTest {
    final CoinType BTC = BitcoinMain.get();
    final CoinType LTC = LitecoinMain.get();
    final CoinType PPC = PeercoinMain.get();
    final CoinType NBT = NuBitsMain.get();
    final FiatType USD = FiatType.get("USD");

    ValueType[] types = {BTC, LTC, NBT, USD};

    @Test
    public void parseCoin() {
        assertEquals(13370000, BTC.value("0.1337").value);
        assertEquals(13370000, BTC.value(".1337").value);

        // Bitcoin family
        assertEquals(133700000, BTC.value("1.337").value);
        assertEquals(133700, BTC.value("0.001337").value);

        // Peercoin family
        assertEquals(1337000, PPC.value("1.337").value);
        assertEquals(1337, PPC.value("0.001337").value);

        // NuBits family
        assertEquals(13370, NBT.value("1.337").value);
        assertEquals(13, NBT.value("0.0013").value);
    }

    @Test(expected = ArithmeticException.class)
    public void parseCoinErrorBitcoin() {
        BTC.value("3.141592653589793");
    }

    @Test(expected = ArithmeticException.class)
    public void parseCoinErrorPeercoin() {
        PPC.value("3.14159265");
    }

    @Test
    public void testParseValue() {
        for (ValueType type : types) {
            runTestParseValue(type);
        }
    }

    public void runTestParseValue(ValueType type) {
        // String version
        Value cent = type.oneCoin().divide(100);
        assertEquals(cent, parse(type, "0.01"));
        assertEquals(cent, parse(type, "1E-2"));
        assertEquals(type.oneCoin().add(cent), parse(type, "1.01"));
        assertEquals(type.oneCoin().negate(), parse(type, "-1"));
        try {
            parse(type, "2E-20");
            org.junit.Assert.fail("should not have accepted fractional satoshis");
        } catch (ArithmeticException e) {}
    }


    @Test
    public void testParseValue2() {
        for (ValueType type : types) {
            runTestParseValue2(type);
        }
    }

    public void runTestParseValue2(ValueType type) {
        // BigDecimal version
        Value cent = type.oneCoin().divide(100);
        BigDecimal bigDecimalCent = BigDecimal.ONE.divide(new BigDecimal(100));
        assertEquals(cent, parse(type, bigDecimalCent));
        assertEquals(type.oneCoin().add(cent), parse(type, BigDecimal.ONE.add(bigDecimalCent)));
        assertEquals(type.oneCoin().negate(), parse(type, BigDecimal.ONE.negate()));
        try {
            parse(type, new BigDecimal("2E-20"));
            org.junit.Assert.fail("should not have accepted fractional satoshis");
        } catch (ArithmeticException e) {}
    }

    @Test
    public void testValueOf() {
        for (ValueType type : types) {
            runTestValueOf(type);
        }
    }

    public void runTestValueOf(ValueType type) {
        try {
            valueOf(type, 1, -1);
            fail();
        } catch (IllegalArgumentException e) {}
        try {
            valueOf(type, -1, 0);
            fail();
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testOperators() {
        for (ValueType type : types) {
            runTestOperators(type);
        }
    }

    public void runTestOperators(ValueType type) {
        assertTrue(valueOf(type, 1).isPositive());
        assertFalse(valueOf(type, 1).isNegative());
        assertFalse(valueOf(type, 1).isZero());
        assertFalse(valueOf(type, -1).isPositive());
        assertTrue(valueOf(type, -1).isNegative());
        assertFalse(valueOf(type, -1).isZero());
        assertFalse(valueOf(type, 0).isPositive());
        assertFalse(valueOf(type, 0).isNegative());
        assertTrue(valueOf(type, 0).isZero());

        assertTrue(valueOf(type, 2).isGreaterThan(valueOf(type, 1)));
        assertFalse(valueOf(type, 2).isGreaterThan(valueOf(type, 2)));
        assertFalse(valueOf(type, 1).isGreaterThan(valueOf(type, 2)));
        assertTrue(valueOf(type, 1).isLessThan(valueOf(type, 2)));
        assertFalse(valueOf(type, 2).isLessThan(valueOf(type, 2)));
        assertFalse(valueOf(type, 2).isLessThan(valueOf(type, 1)));
    }

    @Test
    public void testEquals() {
        Value btcSatoshi = Value.valueOf(BitcoinMain.get(), 1);
        Value btcSatoshi2 = Value.valueOf(BitcoinMain.get(), 1);
        Value btcValue = Value.parse(BitcoinMain.get(), "3.14159");
        Value ltcSatoshi = Value.valueOf(LitecoinMain.get(), 1);
        Value ppcValue = Value.parse(PeercoinMain.get(), "3.14159");

        assertTrue(btcSatoshi.equals(btcSatoshi2));
        assertFalse(btcSatoshi.equals(ltcSatoshi));
        assertFalse(btcSatoshi.equals(btcValue));
        assertFalse(btcSatoshi.equals(ppcValue));
        assertFalse(btcValue.equals(ppcValue));
    }

    @Test
    public void testIsOfType() {
        assertTrue(BTC.oneCoin().isOfType(BTC));
        assertTrue(BTC.oneCoin().isOfType(BTC.oneCoin()));
        assertFalse(BTC.oneCoin().isOfType(LTC));
        assertFalse(BTC.oneCoin().isOfType(LTC.oneCoin()));
    }

    @Test
    public void testWithin() {
        assertTrue(BTC.value("5").within(BTC.value("1"), BTC.value("10")));
        assertTrue(BTC.value("1").within(BTC.value("1"), BTC.value("10")));
        assertTrue(BTC.value("10").within(BTC.value("1"), BTC.value("10")));
        assertFalse(BTC.value("0.1").within(BTC.value("1"), BTC.value("10")));
        assertFalse(BTC.value("11").within(BTC.value("1"), BTC.value("10")));
    }

    @Test
    public void testMathOperators() {
        assertEquals(BTC.value("3.14159"), BTC.value("3").add(BTC.value(".14159")));
        assertEquals(BTC.value("2"), BTC.oneCoin().add(Coin.COIN));
        assertEquals(LTC.value("1"), LTC.value("100").subtract(LTC.value("99")));
        assertEquals(LTC.value("1"), LTC.value("100").subtract("99"));
        assertEquals(100L, USD.value("100").divide(USD.value("1")));
        assertArrayEquals(new Value[]{NBT.value("0.0001"), NBT.value("0.0002")},
                NBT.value("0.0012").divideAndRemainder(10));
        // max
        assertEquals(BTC.value("10"), Value.max(BTC.value("1"), BTC.value("10")));
        assertEquals(BTC.value("0.5"), Value.max(BTC.value("0.5"), BTC.value("-0.5")));
        assertEquals(BTC.value("1"), Value.max(BTC.value("1"), BTC.value("0")));
        // min
        assertEquals(BTC.value("1"), Value.min(BTC.value("1"), BTC.value("10")));
        assertEquals(BTC.value("-0.5"), Value.min(BTC.value("0.5"), BTC.value("-0.5")));
        assertEquals(BTC.value("0"), Value.min(BTC.value("1"), BTC.value("0")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddFail() {
        BTC.oneCoin().add(LTC.oneCoin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubtractFail() {
        BTC.oneCoin().subtract(LTC.oneCoin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDivideFail() {
        BTC.oneCoin().divide(LTC.oneCoin());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCompareFail() {
        BTC.oneCoin().divide(LTC.oneCoin());
    }
}
