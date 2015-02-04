package com.coinomi.core.util;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.NuSharesMain;
import com.coinomi.core.coins.PeercoinMain;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author John L. Jegutanis
 */
public class GenericUtilsTests {

    @Test
    public void parseCoin() {
        // Bitcoin family
        assertEquals(Coin.valueOf(133700000), GenericUtils.parseCoin(BitcoinMain.get(), "1.337"));
        assertEquals(Coin.valueOf(133700), GenericUtils.parseCoin(BitcoinMain.get(), "0.001337"));

        // Peercoin family
        assertEquals(Coin.valueOf(1337000), GenericUtils.parseCoin(PeercoinMain.get(), "1.337"));
        assertEquals(Coin.valueOf(1337), GenericUtils.parseCoin(PeercoinMain.get(), "0.001337"));

        // NuBits family
        assertEquals(Coin.valueOf(13370), GenericUtils.parseCoin(NuBitsMain.get(), "1.337"));
        assertEquals(Coin.valueOf(13), GenericUtils.parseCoin(NuSharesMain.get(), "0.0013"));
    }

    @Test(expected = ArithmeticException.class)
    public void parseCoinErrorBitcoin() {
        GenericUtils.parseCoin(BitcoinMain.get(), "3.141592653589793");
    }

    @Test(expected = ArithmeticException.class)
    public void parseCoinErrorPeercoin() {
        GenericUtils.parseCoin(PeercoinMain.get(), "3.14159265");
    }

    @Test
    public void formatValue() {
        // Bitcoin family
        assertEquals("1.3370", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(133700000), 6, 0));
        assertEquals("0.001337", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(133700), 6, 0));
        assertEquals("1.3370", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(133700000), 4, 0));
        assertEquals("0.0013", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(133700), 4, 0));
        assertEquals("1.34", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(133700000), 2, 0));
        assertEquals("1.34", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(133700000), 2, 0));
        assertEquals("0.0013", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(133700), 2, 0));

        assertEquals("1.00", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100000001), 6, 0));
        assertEquals("1.00000001", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100000001), 8, 0));
        assertEquals("1.00", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100000010), 6, 0));
        assertEquals("1.00000010", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100000010), 8, 0));
        assertEquals("1.000001", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100000100), 6, 0));
        assertEquals("1.000001", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100000100), 8, 0));
        assertEquals("1.000010", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100001000), 6, 0));
        assertEquals("1.000010", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100001000), 8, 0));
        assertEquals("1.0010", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100100001), 6, 0));
        assertEquals("1.00100001", GenericUtils.formatCoinValue(BitcoinMain.get(), Coin.valueOf(100100001), 8, 0));

        // Peercoin family
        assertEquals("3.141592", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(3141592), 6, 0));
        assertEquals("0.027182", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(27182), 6, 0));
        assertEquals("3.1416", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(3141592), 4, 0));
        assertEquals("0.0272", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(27182), 4, 0));
        assertEquals("3.14", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(3141592), 2, 0));
        assertEquals("0.03", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(27182), 2, 0));

        assertEquals("1.00", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000001), 4, 0));
        assertEquals("1.000001", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000001), 6, 0));
        assertEquals("1.000001", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000001), 8, 0));
        assertEquals("1.00", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000010), 4, 0));
        assertEquals("1.000010", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000010), 6, 0));
        assertEquals("1.000010", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000010), 8, 0));
        assertEquals("1.0001", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000100), 4, 0));
        assertEquals("1.0001", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000100), 6, 0));
        assertEquals("1.0001", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000100), 8, 0));
        assertEquals("1.0010", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1001000), 4, 0));
        assertEquals("1.0010", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1001000), 6, 0));
        assertEquals("1.0010", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1001000), 8, 0));
        assertEquals("1.0010", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1001001), 4, 0));
        assertEquals("1.001001", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1001001), 6, 0));
        assertEquals("1.001001", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1001001), 8, 0));

        assertEquals("1.00", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000049), 4, 0));
        assertEquals("1.0001", GenericUtils.formatCoinValue(PeercoinMain.get(), Coin.valueOf(1000050), 4, 0));
    }
}
