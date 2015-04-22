package com.coinomi.core.exchange.shapeshift;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.FiatValue;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftExchangeRate;
import com.coinomi.core.util.ExchangeRateBase;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author John L. Jegutanis
 */
public class ExchangeRateTest {
    final CoinType BTC = BitcoinMain.get();
    final CoinType DOGE = DogecoinMain.get();
    final CoinType NBT = NuBitsMain.get();


    @Test
    public void baseFee() {
        ShapeShiftExchangeRate rate = new ShapeShiftExchangeRate(BTC, NBT, "100", "0.01");

        assertEquals(BTC.value("1"), rate.value1);
        assertEquals(NBT.value("100"), rate.value2);
        assertEquals(NBT.value("0.01"), rate.minerFee);

        assertEquals(NBT.value("99.99"), rate.convert(BTC.oneCoin()));
        assertEquals(BTC.value("1"), rate.convert(NBT.value("99.99")));

        rate = new ShapeShiftExchangeRate(BTC.oneCoin(),
                DOGE.value("1911057.69230769"), DOGE.value("1"));
        assertEquals(BTC.value("1"), rate.value1);
        assertEquals(DOGE.value("1911057.69230769"), rate.value2);
        assertEquals(DOGE.value("1"), rate.minerFee);
        assertEquals(BTC.value("0.00052379"), rate.convert(DOGE.value("1000")));

        rate = new ShapeShiftExchangeRate(BTC.oneCoin(),
                DOGE.value("1878207.54716981"), DOGE.value("1"));
        assertEquals(BTC.value("1"), rate.value1);
        assertEquals(DOGE.value("1878207.54716981"), rate.value2);
        assertEquals(DOGE.value("1"), rate.minerFee);
        assertEquals(BTC.value("0.00532476"), rate.convert(DOGE.value("10000")));
    }

    @Test
    public void zeroValues() {
        ShapeShiftExchangeRate rate = new ShapeShiftExchangeRate(BTC, NBT, "100", "0.01");
        assertEquals(BTC.value("0"), rate.convert(NBT.value("0")));
        assertEquals(NBT.value("0"), rate.convert(BTC.value("0")));
    }

    @Test
    public void smallValues() {
        ShapeShiftExchangeRate rate = new ShapeShiftExchangeRate(DOGE, BTC, "5.1e-7", "0.0001");
        assertEquals(BTC.value("0"), rate.convert(DOGE.value("1")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroExchangeRate() throws Exception {
        new ShapeShiftExchangeRate(BTC, NBT, "0", "0.01");
    }
}