package com.coinomi.core.util;

/*
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

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.FiatType;
import com.coinomi.core.coins.FiatValue;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.Value;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExchangeRateTest {
    final CoinType BTC = BitcoinMain.get();
    final CoinType LTC = LitecoinMain.get();
    final CoinType NBT = NuBitsMain.get();
    final Value oneBtc = BTC.oneCoin();
    final Value oneNbt = NBT.oneCoin();

    @Test
    public void getOtherType() throws Exception {
        ExchangeRateBase rate = new ExchangeRateBase(oneBtc, LTC.oneCoin().multiply(100));

        assertEquals(BTC, rate.getOtherType(LTC));
        assertEquals(LTC, rate.getOtherType(BTC));
    }

    @Test
    public void canConvert() throws Exception {
        ExchangeRateBase rate = new ExchangeRateBase(oneBtc, oneNbt);

        assertTrue(rate.canConvert(BTC, NBT));
        assertTrue(rate.canConvert(NBT, BTC));
        assertFalse(rate.canConvert(LTC, BTC));
        assertFalse(rate.canConvert(LTC, FiatType.get("USD")));
    }

    @Test
    public void cryptoToFiatRate() throws Exception {
        ExchangeRateBase rate = new ExchangeRateBase(oneBtc, FiatValue.parse("EUR", "500"));

        assertEquals(FiatType.get("EUR"), rate.convert(oneBtc).type);
        assertEquals(BTC, rate.convert(FiatValue.parse("EUR", "1")).type);

        assertEquals("0.5", rate.convert(oneBtc.divide(1000)).toPlainString());
        assertEquals("0.002", rate.convert(FiatValue.parse("EUR", "1")).toPlainString());
    }

    @Test
    public void cryptoToCryptoRate() throws Exception {
        // 1BTC = 100LTC
        ExchangeRateBase rate = new ExchangeRateBase(oneBtc, LTC.oneCoin().multiply(100));

        assertEquals(LTC, rate.convert(oneBtc).type);
        assertEquals(BTC, rate.convert(LTC.oneCoin()).type);

        assertEquals("1", rate.convert(oneBtc.divide(100)).toPlainString());
        assertEquals("0.01", rate.convert(LTC.oneCoin()).toPlainString());

        // 250NBT = 1BTC
        rate = new ExchangeRateBase(oneNbt.multiply(250), oneBtc);
        assertEquals("0.004", rate.convert(oneNbt).toPlainString());
        assertEquals("2500", rate.convert(oneBtc.multiply(10)).toPlainString());
    }

    @Test
    public void cryptoToCryptoRateParseConstructor() throws Exception {
        // 1BTC = 100LTC
        ExchangeRateBase rate = new ExchangeRateBase(BTC, LTC, "100");

        assertEquals(LTC, rate.convert(oneBtc).type);
        assertEquals(BTC, rate.convert(LTC.oneCoin()).type);

        assertEquals("1", rate.convert(oneBtc.divide(100)).toPlainString());
        assertEquals("0.01", rate.convert(LTC.oneCoin()).toPlainString());
    }

    @Test
    public void scalingAndRounding() throws Exception {
        // 1BTC = 100.1234567890NBT
        // This rate causes the BTC & NBT to overflow so it sets the correct scale and rounding
        ExchangeRateBase rate = new ExchangeRateBase(BTC, NBT, "100.1234567890");

        // Check the rate's internal state
        assertEquals("100000", rate.value1.toPlainString());
        assertEquals("10012345.6789", rate.value2.toPlainString());

        // Make some conversions
        assertEquals("0.00998767", rate.convert(oneNbt).toPlainString());
        assertEquals("0.000001", rate.convert(Value.parse(NBT, "0.0001")).toPlainString());
        assertEquals("0.0001", rate.convert(Value.parse(BTC, "0.00000099")).toPlainString());
        assertEquals("0.0099", rate.convert(Value.parse(BTC, "0.000099")).toPlainString());
        assertEquals("10012345.6789", rate.convert(oneBtc.multiply(100000)).toPlainString());
        assertEquals("1001.2346", rate.convert(oneBtc.multiply(10)).toPlainString());
        assertEquals("998766.95438852", rate.convert(oneNbt.multiply(100000000)).toPlainString());

        // Check too precise rates
        rate = new ExchangeRateBase(BTC, NBT, "100.12345678901999");
        assertEquals("100000", rate.value1.toPlainString());
        assertEquals("10012345.6789", rate.value2.toPlainString());
    }

    @Test
    public void bigRate() throws Exception {
        ExchangeRateBase rate = new ExchangeRateBase(Value.parse(BTC, "0.0001"), FiatValue.parse("BYR", "5320387.3"));
        assertEquals("53203873000", rate.convert(oneBtc).toPlainString());
        assertEquals("0", rate.convert(FiatValue.parse("BYR", "1")).toPlainString()); // Tiny value!
    }

    @Test
    public void smallRate() throws Exception {
        ExchangeRateBase rate = new ExchangeRateBase(Value.parse(BTC, "10000"), FiatValue.parse("XXX", "0.00001"));
        assertEquals("0", rate.convert(oneBtc).toPlainString()); // Tiny value!
        assertEquals("1000000000", rate.convert(FiatValue.parse("XXX", "1")).toPlainString());
    }

    @Test
    public void zeroValues() {
        ExchangeRateBase rate = new ExchangeRateBase(BTC.value("1"), FiatValue.parse("XXX", "100"));
        assertEquals(BTC.value("0"), rate.convert(FiatValue.parse("XXX", "0")));
        assertEquals(FiatValue.parse("XXX", "0"), rate.convert(BTC.value("0")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroExchangeRate() throws Exception {
        new ExchangeRateBase(oneBtc, LTC.value(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void currencyCodeMismatch() throws Exception {
        ExchangeRateBase rate = new ExchangeRateBase(oneBtc, FiatValue.parse("EUR", "500"));
        rate.convert(FiatValue.parse("USD", "1"));
    }

    @Test(expected = ArithmeticException.class)
    public void coinToFiatTooLarge() throws Exception {
        ExchangeRateBase rate = new ExchangeRateBase(oneBtc, FiatValue.parse("XXX", "1000000000"));
        rate.convert(Value.parse(BTC, "1000000"));
    }

    @Test(expected = ArithmeticException.class)
    public void coinToFiatTooSmall() throws Exception {
        ExchangeRateBase rate = new ExchangeRateBase(oneBtc, FiatValue.parse("XXX", "1000000000"));
        rate.convert(Value.parse(BTC, "-1000000"));
    }
}
