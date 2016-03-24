package com.coinomi.core.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.BIP38PrivateKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author John L. Jegutanis
 */
public class SweepWalletTest {
    static final CoinType BTC = BitcoinMain.get();
    static final CoinType DOGE = DogecoinMain.get();
    static final String BTC_WIF_ADDR = "15uucyG2PdLRpYU1UDNucMzxm56UbVv2YG";
    static final String BTC_WIF_PRIV = "5KT6EseSW2NxYDw66aPsqvyv6cKunUSNHUVkTtxyaiGspb3XJVz";
    static final String BTC_BIP38_WIF_PRIV = "6PRSEfu2G7mya8YoaQo4Y5iSysAKy8VpixN574SXeh7VvCcgkNMS6BeHgm";
    static final String BTC_BIP38_ADDR = "1BBNgmuc8hwaaGqawbPYbnD1ibr9AMSKEo";
    static final String BTC_BIP38_PRIV = "6PRVb3WLvM3wk5U32jaENjQBxvP8fRtq2g7UpBfmZ1FLJ1ZxNLmD57yv8s";
    static final String BTC_MINI_PRIV = "S6c56bnXQiBjk9mqSYE7ykVQ7NzrRy";
    static final String BTC_MINI_ADDR = "1CciesT23BNionJeXrbxmjc7ywfiyM4oLW";
    static final String DOGE_BIP38_ADDR = "D5LBizi5ZzsVxwpfuFBaP7VSu4B1NRnx5c";
    static final String DOGE_BIP38_PRIV = "6PRWBkdj7oqcgu5TcNuqVXt51tcLG3ys6597WFwD2Qvmf8BRqJrgXaUBsu";
    static final String BIP38_PASS = "coinomi";

    @Test
    public void testWif() throws Exception {
        SerializedKey serializedKey = new SerializedKey(BTC_WIF_PRIV);
        assertFalse(serializedKey.isEncrypted());
        SerializedKey.TypedKey key = serializedKey.getKey();
        assertTrue(key.possibleType.contains(BTC));
        assertEquals(BTC_WIF_ADDR, key.key.toAddress(BTC).toString());
    }

    @Test
    public void testBip38() throws Exception {
        SerializedKey serializedKey = new SerializedKey(BTC_BIP38_WIF_PRIV);
        assertTrue(serializedKey.isEncrypted());
        SerializedKey.TypedKey key = serializedKey.getKey(BIP38_PASS);
        assertTrue(key.possibleType.contains(BTC));
        assertEquals(BTC_WIF_ADDR, key.key.toAddress(BTC).toString());

        serializedKey = new SerializedKey(BTC_BIP38_PRIV);
        assertTrue(serializedKey.isEncrypted());
        key = serializedKey.getKey(BIP38_PASS);
        assertTrue(key.possibleType.contains(BTC));
        assertEquals(BTC_BIP38_ADDR, key.key.toAddress(BTC).toString());

        serializedKey = new SerializedKey(DOGE_BIP38_PRIV);
        assertTrue(serializedKey.isEncrypted());
        key = serializedKey.getKey(BIP38_PASS);
        assertTrue(key.possibleType.contains(DOGE));
        assertEquals(DOGE_BIP38_ADDR, key.key.toAddress(DOGE).toString());
    }

    @Test
    public void testMini() throws Exception {
        SerializedKey serializedKey = new SerializedKey(BTC_MINI_PRIV);
        assertFalse(serializedKey.isEncrypted());
        SerializedKey.TypedKey key = serializedKey.getKey();
        assertTrue(key.possibleType.contains(BTC));
        assertEquals(BTC_MINI_ADDR, key.key.toAddress(BTC).toString());
    }
}
