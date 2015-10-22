package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinTest;
import com.coinomi.core.coins.NxtMain;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.families.NxtFamily;
import com.coinomi.core.coins.nxt.Account;
import com.coinomi.core.coins.nxt.Appendix;
import com.coinomi.core.coins.nxt.Attachment;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.Crypto;
import com.coinomi.core.coins.nxt.EncryptedData;
import com.coinomi.core.coins.nxt.NxtException;
import com.coinomi.core.coins.nxt.Transaction;
import com.coinomi.core.coins.nxt.TransactionImpl;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletPocketHD;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.wallet.DeterministicSeed;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author John L. Jegutanis
 */
public class NxtFamilyWalletTest {
    CoinType NXT = NxtMain.get();
    String recoveryPhrase = "heavy virus hollow shrug shadow double dwarf affair novel weird image prize frame anxiety wait";
    String nxtSecret = "check federal prize adapt pumpkin renew toilet flip candy leopard reform leaf venture hammer amateur rack coyote hover under clog pitch cash begin issue";
    String nxtRsAddress = "NXT-BE3N-2KJX-ZMQJ-4GW4P";
    long nxtAccountId = Convert.parseAccountId(NXT, "3065700120828096564");
    byte[] nxtPublicKey = Hex.decode("195df5f2e4d826fd512d3748eb2c47a9a0d59cdd18b6a0f3b4717381b8f9ac59");

    DeterministicHierarchy hierarchy;
    static final byte[] aesKeyBytes = {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7};
    KeyParameter aesKey = new KeyParameter(aesKeyBytes);
    KeyCrypter crypter = new KeyCrypterScrypt();
    Wallet wallet;
    NxtFamilyWallet nxtAccount;
    NxtFamilyWallet otherAccount;

    @Before
    public void setup() throws MnemonicException, UnreadableWalletException {
        DeterministicSeed seed = new DeterministicSeed(recoveryPhrase, null, "", 0);
        DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        hierarchy = new DeterministicHierarchy(masterKey);
        wallet = new Wallet(recoveryPhrase);
        nxtAccount = (NxtFamilyWallet)wallet.createAccount(NXT, null);
        otherAccount = new NxtFamilyWallet(hierarchy.get(NXT.getBip44Path(1), false, true), NXT);
    }

    @Test
    public void serializeUnencryptedNormal() throws Exception {
        List<Protos.Key> keys = nxtAccount.serializeKeychainToProtobuf();

        NxtFamilyKey nxtKey = NxtFamilyKey.fromProtobuf(keys);

        NxtFamilyWallet newWallet;
        newWallet = new NxtFamilyWallet(nxtKey, NXT);

    }


    @Test
    public void testAccountLegacyNXT() throws UnsupportedEncodingException {
        byte[] pub = nxtAccount.getPublicKey();
        long id = Account.getId(pub);

        assertArrayEquals(nxtPublicKey, pub);
        assertEquals(nxtRsAddress, Convert.rsAccount(NxtMain.get(), id));
        assertEquals(nxtAccountId, id);
    }

    @Test
    public void testHDAccountNxt() throws MnemonicException {
        String secret = nxtAccount.getPrivateKeyMnemonic();
        byte[] pub = nxtAccount.getPublicKey();

        assertEquals(nxtSecret, secret);
        assertEquals(nxtRsAddress, nxtAccount.getPublicKeyMnemonic());
        assertArrayEquals(nxtPublicKey, pub);
        NxtFamilyAddress address = (NxtFamilyAddress) nxtAccount.getReceiveAddress();
        assertEquals(nxtRsAddress, address.toString());
        assertEquals(nxtAccountId, address.getAccountId());
    }

    @Test
    public void testNxtTransaction() throws WalletAccount.WalletAccountException, NxtException.ValidationException {
        NxtFamilyAddress destination = (NxtFamilyAddress) otherAccount.getReceiveAddress();
        Value amount = NXT.value("1");
        SendRequest req = nxtAccount.sendCoinsOffline(destination, amount);
        //nxtAccount
        //nxtAccount.completeAndSignTx(req);



        Transaction nxtTx = req.nxtTxBuilder.build();
        nxtTx.sign(nxtSecret);


        byte[] txBytes = req.nxtTxBuilder.build().getBytes();

        req.tx = new NxtTransaction(req.nxtTxBuilder.build());

        Transaction parsedTx = TransactionImpl.parseTransaction(txBytes);
        assertEquals(Attachment.ORDINARY_PAYMENT, parsedTx.getAttachment());
        assertEquals(NxtFamily.DEFAULT_DEADLINE, parsedTx.getDeadline());
        System.out.println(((Transaction) req.tx.getTransaction()).getTimestamp());
        assertEquals(((Transaction) req.tx.getTransaction()).getTimestamp(), parsedTx.getTimestamp());
        assertEquals(nxtAccountId, parsedTx.getSenderId());
        assertArrayEquals(nxtPublicKey, parsedTx.getSenderPublicKey());
        assertEquals(amount.value, parsedTx.getAmountNQT());
        assertEquals(req.fee.value, parsedTx.getFeeNQT());
        assertEquals(destination.getAccountId(), parsedTx.getRecipientId());

        System.out.println(Convert.toHexString(nxtTx.getBytes()));
        // TODO check signature
    }

    @Test
    public void testSerializeKeychainToProtobuf() throws UnreadableWalletException
    {
        List<Protos.Key> keys = nxtAccount.serializeKeychainToProtobuf();

        NxtFamilyKey newKey = NxtFamilyKey.fromProtobuf(keys);

        NxtFamilyWallet newWallet = new NxtFamilyWallet(newKey, NXT);

        assertEquals(Convert.toHexString(nxtAccount.getPublicKey()), Convert.toHexString(newWallet.getPublicKey()));
        assertEquals(nxtAccount.getPublicKeyMnemonic(), newWallet.getPublicKeyMnemonic());
        assertEquals(nxtAccount.getId(), newWallet.getId());

    }

    @Test
    public void testEncryptedNxtFamilyKey() throws UnreadableWalletException
    {
        nxtAccount.encrypt(crypter, aesKey);

        List<Protos.Key> keys = nxtAccount.serializeKeychainToProtobuf();

        NxtFamilyKey newKey = NxtFamilyKey.fromProtobuf(keys, crypter);

        NxtFamilyWallet newWallet = new NxtFamilyWallet(newKey, NXT);

        assertEquals(Convert.toHexString(nxtAccount.getPublicKey()), Convert.toHexString(newWallet.getPublicKey()));
        assertEquals(nxtAccount.getPublicKeyMnemonic(), newWallet.getPublicKeyMnemonic());
        assertEquals(nxtAccount.getId(), newWallet.getId());
        
    }


}
