package com.coinomi.core.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.DogecoinTest;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.protos.Protos;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.utils.BriefLogFormatter;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * @author John L. Jegutanis
 */
public class WalletTest {
    static final List<String> MNEMONIC = ImmutableList.of("citizen", "fever", "scale", "nurse", "brief", "round", "ski", "fiction", "car", "fitness", "pluck", "act");
    CoinType type = DogecoinTest.get();
    private Wallet wallet;
    static final byte[] aesKeyBytes = {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7};
    KeyParameter aesKey = new KeyParameter(aesKeyBytes);
    KeyCrypter crypter = new KeyCrypterScrypt();

    @Before
    public void setup() throws IOException, MnemonicException {
        BriefLogFormatter.init();

        wallet = new Wallet(MNEMONIC);

        ImmutableList<CoinType> typesToCreate = ImmutableList.of(BitcoinMain.get(),
                LitecoinMain.get(), DogecoinMain.get());
        wallet.createAccounts(typesToCreate, true, aesKey);
    }

    @Test
    public void serializeUnencryptedNormal() throws Exception {
        wallet.maybeInitializeAllPockets();

        Protos.Wallet walletProto = wallet.toProtobuf();

        Wallet newWallet = WalletProtobufSerializer.readWallet(walletProto);

        assertEquals(walletProto.toString(), newWallet.toProtobuf().toString());
        assertArrayEquals(MNEMONIC.toArray(), newWallet.getMnemonicCode().toArray());
    }


    @Test
    public void serializeEncryptedNormal() throws Exception {
        wallet.maybeInitializeAllPockets();
        wallet.encrypt(crypter, aesKey);

        assertNull(wallet.getSeed().getMnemonicCode());

        Protos.Wallet walletProto = wallet.toProtobuf();

        Wallet newWallet = WalletProtobufSerializer.readWallet(walletProto);

        assertEquals(walletProto.toString(), newWallet.toProtobuf().toString());

        wallet.decrypt(aesKey);

        // One is encrypted, so they should not match
        assertNotEquals(wallet.toProtobuf().toString(), newWallet.toProtobuf().toString());

        newWallet.decrypt(aesKey);

        assertEquals(wallet.toProtobuf().toString(), newWallet.toProtobuf().toString());

        assertArrayEquals(MNEMONIC.toArray(), newWallet.getMnemonicCode().toArray());
    }


}
