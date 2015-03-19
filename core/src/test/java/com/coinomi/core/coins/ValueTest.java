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


import static com.coinomi.core.coins.Value.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ValueTest {
    ValueType[] types = {BitcoinMain.get(), LitecoinMain.get(), NuBitsMain.get(), FiatType.get("USD")};

    @Test
    public void testParseCoin() {
        for (ValueType type : types) {
            runTestParseCoin(type);
        }
    }

    public void runTestParseCoin(ValueType type) {
        // String version
        Value cent = type.oneCoin().divide(100);
        assertEquals(cent, parseValue(type, "0.01"));
        assertEquals(cent, parseValue(type, "1E-2"));
        assertEquals(type.oneCoin().add(cent), parseValue(type, "1.01"));
        assertEquals(type.oneCoin().negate(), parseValue(type, "-1"));
        try {
            parseValue(type, "2E-20");
            org.junit.Assert.fail("should not have accepted fractional satoshis");
        } catch (ArithmeticException e) {
        }
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
}
