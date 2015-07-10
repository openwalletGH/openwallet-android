package com.coinomi.core.coins.nxt;

/**
 * @author John L. Jegutanis
 */

import com.coinomi.core.coins.NxtMain;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.families.nxt.NxtFamilyAddress;
import com.coinomi.core.wallet.families.nxt.NxtFamilyWallet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.bitcoinj.crypto.MnemonicException;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;

public class NxtFamilyTest {
    String recoveryPhrase = "heavy virus hollow shrug shadow double dwarf affair novel weird image prize frame anxiety wait";
    String nxtSecret = "check federal prize adapt pumpkin renew toilet flip candy leopard reform leaf venture hammer amateur rack coyote hover under clog pitch cash begin issue";
    String nxtRsAddress = "NXT-BE3N-2KJX-ZMQJ-4GW4P";
    long nxtAccountId = Convert.parseAccountId("3065700120828096564");
    private byte[] nxtPublicKey = Hex.decode("195df5f2e4d826fd512d3748eb2c47a9a0d59cdd18b6a0f3b4717381b8f9ac59");

    @Test
    public void testAccountLegacyNXT() throws UnsupportedEncodingException {
        byte[] pub = Crypto.getPublicKey(nxtSecret);
        long id = Account.getId(pub);

        assertArrayEquals(nxtPublicKey, pub);
        assertEquals(nxtRsAddress, Convert.rsAccount(NxtMain.get(), id));
        assertEquals(nxtAccountId, id);
    }

    @Test
    public void testHDAccountNxt() throws MnemonicException {
        Wallet wallet = new Wallet(recoveryPhrase);
        NxtFamilyWallet account = (NxtFamilyWallet) wallet.createAccount(NxtMain.get(), true, null);
        String secret = account.getPrivateKeyMnemonic();
        byte[] pub = account.getPublicKey();

        assertEquals(nxtSecret, secret);
        assertEquals(nxtRsAddress, account.getPublicKeyMnemonic());
        assertArrayEquals(nxtPublicKey, pub);
        NxtFamilyAddress address = (NxtFamilyAddress) account.getReceiveAddress();
        assertEquals(nxtRsAddress, address.toString());
        assertEquals(nxtAccountId, address.getAccountId());
    }

    // TODO add private key tests for Burst
    // Transaction creation for NXT and Burst
}
