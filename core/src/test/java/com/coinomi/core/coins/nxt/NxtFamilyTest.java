package com.coinomi.core.coins.nxt;

/**
 * @author John L. Jegutanis
 */

import com.coinomi.core.coins.NxtMain;
import com.coinomi.core.coins.nxt.Appendix.EncryptedMessage;
import com.coinomi.core.wallet.families.nxt.NxtAddress;
import com.coinomi.core.wallet.families.nxt.NxtFamilyKey;

import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.wallet.DeterministicSeed;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class NxtFamilyTest {
    String recoveryPhrase = "heavy virus hollow shrug shadow double dwarf affair novel weird image prize frame anxiety wait";
    byte[] nxtPrivateKey = Hex.decode("200a8ead018adb6c78f2c821500ad13f5f24d101ed8431adcfb315ca58468553");
    byte[] nxtPublicKey = Hex.decode("163c6583ed489414f27e73a74f72080b478a55dfce4a086ded2990976e8bb81e");
    String nxtRsAddress = "NXT-CGNQ-8WBM-3P2F-AVH9J";
    long nxtAccountId = Convert.parseAccountId(NxtMain.get(), "9808271777446836886");

    String recipient = "NXT-RZ9H-H2XD-WTR3-B4WN2";
    byte[] recipientPublicKey = Convert.parseHexString("8381e8668479d27316dced97429c2bf7fde9d909cce2c53d565a4078ee82b13a");

    @Test
    public void testAccountNxt() throws UnsupportedEncodingException {
        byte[] publicKey = Crypto.getPublicKey(nxtPrivateKey);
        long id = Account.getId(publicKey);

        assertArrayEquals(nxtPublicKey, publicKey);
        assertEquals(nxtRsAddress, Convert.rsAccount(NxtMain.get(), id));
        assertEquals(nxtAccountId, id);
    }

    @Test
    public void testHDAccountNxt() throws MnemonicException, UnreadableWalletException {
        DeterministicSeed seed = new DeterministicSeed(recoveryPhrase, null, "", 0);
        DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterKey);
        DeterministicKey entropy = hierarchy.get(NxtMain.get().getBip44Path(0), false, true);

        NxtFamilyKey nxtKey = new NxtFamilyKey(entropy, null, null);
        byte[] privateKey = nxtKey.getPrivateKey();
        byte[] publicKey = nxtKey.getPublicKey();
        NxtAddress address = new NxtAddress(NxtMain.get(), publicKey);

        assertArrayEquals(nxtPrivateKey, privateKey);
        assertArrayEquals(nxtPublicKey, publicKey);
        assertEquals(nxtRsAddress, address.toString());
        assertEquals(nxtAccountId, address.getAccountId());
    }

    @Test
    public void testNxtTransaction() throws NxtException.ValidationException {
        byte version = 1;
        long amountNQT = 100000000L;
        long feeNQT = 100000000L;
        int timestamp = Convert.toNxtEpochTime(System.currentTimeMillis()); // different for nxt and burst
        short deadline = 1440;
        long recipientLong = Convert.parseAccountId(NxtMain.get(), recipient);

        TransactionImpl.BuilderImpl builder = new TransactionImpl.BuilderImpl(version, nxtPublicKey,
                amountNQT, feeNQT, timestamp, deadline, Attachment.ORDINARY_PAYMENT);
        if (version > 0) {
            builder.ecBlockHeight(0);
            builder.ecBlockId(0L);
            //Block ecBlock = EconomicClustering.getECBlock(timestamp);
            //builder.ecBlockHeight(ecBlock.getHeight());
            //builder.ecBlockId(ecBlock.getId());
        }
        Transaction transaction = builder.recipientId(recipientLong).build();
        transaction.sign(nxtPrivateKey);
        byte[] txBytes = transaction.getBytes();

        Transaction parsedTx = TransactionImpl.parseTransaction(txBytes);
        assertEquals(Attachment.ORDINARY_PAYMENT, parsedTx.getAttachment());
        assertEquals(deadline, parsedTx.getDeadline());
        assertEquals(timestamp, parsedTx.getTimestamp());
        assertEquals(nxtAccountId, parsedTx.getSenderId());
        assertArrayEquals(nxtPublicKey, parsedTx.getSenderPublicKey());
        assertEquals(amountNQT, parsedTx.getAmountNQT());
        assertEquals(feeNQT, parsedTx.getFeeNQT());
        assertEquals(recipientLong, parsedTx.getRecipientId());
        // TODO check signature
    }

    @Test
    public void testNxtMessageTransaction() throws NxtException.ValidationException {

        byte version = 1;
        Long amountNQT = 0L;
        Long feeNQT = 100000000L;
        int timestamp = Convert.toNxtEpochTime(System.currentTimeMillis()); // different for nxt and burst
        short deadline = 1440;
        Long recipientLong = Convert.parseAccountId(NxtMain.get(), recipient);
        EncryptedData data = EncryptedData.encrypt(Convert.toBytes("test text"), nxtPrivateKey, recipientPublicKey);

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
        transaction.sign(nxtPrivateKey);
        byte[] txBytes = transaction.getBytes();

        Transaction parsedTx = TransactionImpl.parseTransaction(txBytes);
        // TODO add asserts

//        System.out.println(Convert.toHexString(txBytes));
    }

    // TODO add private key tests for Burst
    // Transaction creation for NXT and Burst
}
