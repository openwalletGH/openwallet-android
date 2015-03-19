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
import com.coinomi.core.coins.FiatType;
import com.coinomi.core.coins.FiatValue;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.Value;

import static org.junit.Assert.assertEquals;

import org.bitcoinj.core.NetworkParameters;
import org.junit.Test;

public class ExchangeRateTest {

    @Test
    public void getOtherType() throws Exception {
        ExchangeRate rate = new ExchangeRate(BitcoinMain.get().oneCoin(), LitecoinMain.get().oneCoin().multiply(100));

        assertEquals(BitcoinMain.get(), rate.getOtherType(LitecoinMain.get()));
        assertEquals(LitecoinMain.get(), rate.getOtherType(BitcoinMain.get()));
    }

    @Test
    public void cryptoToFiatRate() throws Exception {
        ExchangeRate rate = new ExchangeRate(BitcoinMain.get().oneCoin(), FiatValue.parse("EUR", "500"));

        assertEquals(FiatType.get("EUR"), rate.convert(BitcoinMain.get().oneCoin()).type);
        assertEquals(BitcoinMain.get(), rate.convert(FiatValue.parse("EUR", "1")).type);

        assertEquals("0.5", rate.convert(BitcoinMain.get().oneCoin().divide(1000)).toPlainString());
        assertEquals("0.002", rate.convert(FiatValue.parse("EUR", "1")).toPlainString());
    }

    @Test
    public void cryptoToCryptoRate() throws Exception {
        // 1BTC = 100LTC
        ExchangeRate rate = new ExchangeRate(BitcoinMain.get().oneCoin(), LitecoinMain.get().oneCoin().multiply(100));

        assertEquals(LitecoinMain.get(), rate.convert(BitcoinMain.get().oneCoin()).type);
        assertEquals(BitcoinMain.get(), rate.convert(LitecoinMain.get().oneCoin()).type);

        assertEquals("1", rate.convert(BitcoinMain.get().oneCoin().divide(100)).toPlainString());
        assertEquals("0.01", rate.convert(LitecoinMain.get().oneCoin()).toPlainString());

        // 250NBT = 1BTC
        rate = new ExchangeRate(NuBitsMain.get().oneCoin().multiply(250), BitcoinMain.get().oneCoin());
        assertEquals("0.004", rate.convert(NuBitsMain.get().oneCoin()).toPlainString());
        assertEquals("2500", rate.convert(BitcoinMain.get().oneCoin().multiply(10)).toPlainString());

    }

    @Test
    public void bigRate() throws Exception {
        ExchangeRate rate = new ExchangeRate(Value.parseValue(BitcoinMain.get(), "0.0001"), FiatValue.parse("BYR", "5320387.3"));
        assertEquals("53203873000", rate.convert(BitcoinMain.get().oneCoin()).toPlainString());
        assertEquals("0", rate.convert(FiatValue.parse("BYR", "1")).toPlainString()); // Tiny value!
    }

    @Test
    public void smallRate() throws Exception {
        ExchangeRate rate = new ExchangeRate(Value.parseValue(BitcoinMain.get(), "1000"), FiatValue.parse("XXX", "0.0001"));
        assertEquals("0", rate.convert(BitcoinMain.get().oneCoin()).toPlainString()); // Tiny value!
        assertEquals("10000000", rate.convert(FiatValue.parse("XXX", "1")).toPlainString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void currencyCodeMismatch() throws Exception {
        ExchangeRate rate = new ExchangeRate(BitcoinMain.get().oneCoin(), FiatValue.parse("EUR", "500"));
        rate.convert(FiatValue.parse("USD", "1"));
    }

    @Test(expected = ArithmeticException.class)
    public void fiatToCoinTooLarge() throws Exception {
        ExchangeRate rate = new ExchangeRate(BitcoinMain.get().oneCoin(), FiatValue.parse("XXX", "1"));
        rate.convert(FiatValue.parse("XXX", String.valueOf(NetworkParameters.MAX_COINS + 1)));
    }

    @Test(expected = ArithmeticException.class)
    public void fiatToCoinTooSmall() throws Exception {
        ExchangeRate rate = new ExchangeRate(BitcoinMain.get().oneCoin(), FiatValue.parse("XXX", "1"));
        rate.convert(FiatValue.parse("XXX", String.valueOf(-1 * (NetworkParameters.MAX_COINS + 1))));
    }

    @Test(expected = ArithmeticException.class)
    public void coinToFiatTooLarge() throws Exception {
        ExchangeRate rate = new ExchangeRate(BitcoinMain.get().oneCoin(), FiatValue.parse("XXX", "1000000000"));
        rate.convert(Value.parseValue(BitcoinMain.get(), "1000000"));
    }

    @Test(expected = ArithmeticException.class)
    public void coinToFiatTooSmall() throws Exception {
        ExchangeRate rate = new ExchangeRate(BitcoinMain.get().oneCoin(), FiatValue.parse("XXX", "1000000000"));
        rate.convert(Value.parseValue(BitcoinMain.get(), "-1000000"));
    }
}
