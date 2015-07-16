package com.coinomi.core.coins.nxt;

/**
 * @author John L. Jegutanis
 */

import com.coinomi.core.coins.NxtMain;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.families.nxt.NxtFamilyAddress;
import com.coinomi.core.wallet.families.nxt.NxtFamilyWallet;
import com.coinomi.core.coins.nxt.Appendix.EncryptedMessage;

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
    byte[] nxtPublicKey = Hex.decode("195df5f2e4d826fd512d3748eb2c47a9a0d59cdd18b6a0f3b4717381b8f9ac59");

    String recipient = "NXT-RZ9H-H2XD-WTR3-B4WN2";
    byte[] recipientPublicKey = Convert.parseHexString("8381e8668479d27316dced97429c2bf7fde9d909cce2c53d565a4078ee82b13a");


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

    @Test
    public void testNxtTransaction() throws NxtException.NotValidException {
        //old way
        //NxtTransaction tx = new NxtTransaction(nxtPublicKey, recipient, 500000000L);
        //byte[] signedTxBytes = tx.getSignedTxBytes(nxtSecret);

        //new way

        byte version = 1;
        Long amountNQT = 100000000L;
        Long feeNQT = 100000000L;
        int timestamp = Convert.toNxtEpochTime(System.currentTimeMillis()); // different for nxt and burst
        short deadline = 1440;
        Long recipientLong = Convert.parseAccountId(recipient);

        TransactionImpl.BuilderImpl builder = new TransactionImpl.BuilderImpl(version, nxtPublicKey, amountNQT, feeNQT, timestamp,
                deadline, (Attachment.AbstractAttachment)Attachment.ORDINARY_PAYMENT);
        if (version > 0) {
            builder.ecBlockHeight(0);
            builder.ecBlockId(0L);
            //Block ecBlock = EconomicClustering.getECBlock(timestamp);
            //builder.ecBlockHeight(ecBlock.getHeight());
            //builder.ecBlockId(ecBlock.getId());

        }
        Transaction transaction = builder.recipientId(recipientLong).build();
        transaction.sign( nxtSecret );
        byte[] txBytes = transaction.getBytes();
        System.out.println(Convert.toHexString(txBytes));
    }

    @Test
    public void testNxtMessageTransaction() throws NxtException.NotValidException {

        byte version = 1;
        Long amountNQT = 0L;
        Long feeNQT = 100000000L;
        int timestamp = Convert.toNxtEpochTime(System.currentTimeMillis()); // different for nxt and burst
        short deadline = 1440;
        Long recipientLong = Convert.parseAccountId(recipient);
        EncryptedData data = EncryptedData.encrypt(Convert.toBytes("test text"), Crypto.getPrivateKey(nxtSecret), recipientPublicKey);

        Appendix.EncryptedMessage msg = new EncryptedMessage(data, true);

        TransactionImpl.BuilderImpl builder;
        builder = new TransactionImpl.BuilderImpl(version, nxtPublicKey, amountNQT, feeNQT, timestamp,
                deadline, Attachment.ARBITRARY_MESSAGE);
        if (version > 0) {
            builder.ecBlockHeight(0);
            builder.ecBlockId(0L);
            //Block ecBlock = EconomicClustering.getECBlock(timestamp);
            //builder.ecBlockHeight(ecBlock.getHeight());
            //builder.ecBlockId(ecBlock.getId());

        }
        builder.encryptedMessage(msg);
        Transaction transaction = builder.recipientId(recipientLong).build();
        transaction.sign( nxtSecret );
        byte[] txBytes = transaction.getBytes();
        System.out.println(Convert.toHexString(txBytes));
    }

    // TODO add private key tests for Burst
    // Transaction creation for NXT and Burst
}
