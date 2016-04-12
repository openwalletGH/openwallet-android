package com.coinomi.core.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinTest;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.VpncoinMain;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.exceptions.Bip44KeyLookAheadExceededException;
import com.coinomi.core.exceptions.KeyIsEncryptedException;
import com.coinomi.core.exceptions.MissingPrivateKeyException;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.ServerClient.HistoryTx;
import com.coinomi.core.network.ServerClient.UnspentTx;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.wallet.families.bitcoin.BitAddress;
import com.coinomi.core.wallet.families.bitcoin.BitBlockchainConnection;
import com.coinomi.core.wallet.families.bitcoin.BitSendRequest;
import com.coinomi.core.wallet.families.bitcoin.BitTransaction;
import com.coinomi.core.wallet.families.bitcoin.BitTransactionEventListener;
import com.coinomi.core.wallet.families.bitcoin.OutPointOutput;
import com.coinomi.core.wallet.families.bitcoin.TrimmedOutPoint;
import com.google.common.collect.ImmutableList;

import org.bitcoinj.core.ChildMessage;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence.Source;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.store.UnreadableWalletException;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChain;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.coinomi.core.Preconditions.checkNotNull;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author John L. Jegutanis
 */
public class WalletPocketHDTest {
    static final CoinType BTC = BitcoinMain.get();
    static final CoinType DOGE = DogecoinTest.get();
    static final CoinType NBT = NuBitsMain.get();
    static final CoinType VPN = VpncoinMain.get();
    static final List<String> MNEMONIC = ImmutableList.of("citizen", "fever", "scale", "nurse", "brief", "round", "ski", "fiction", "car", "fitness", "pluck", "act");
    static final byte[] AES_KEY_BYTES = {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7};
    static final long AMOUNT_TO_SEND = 2700000000L;
    static final String MESSAGE = "test";
    static final String MESSAGE_UNICODE = "δοκιμή испытание 测试";
    static final String EXPECTED_BITCOIN_SIG = "IMBbIFDDuUwomYlvSjwWytqP/CXYym2yOKbJUx8Y+ujzZKBwoCFMr73GUxpr1Ird/DvnNZcsQLphvx18ftqN54o=";
    static final String EXPECTED_BITCOIN_SIG_UNICODE = "IGGZEOBsVny5dozTOfc2/3UuvmZGdDI4XK/03HIk34PILd2oXbnq+87GINT3lumeXcgSO2NkGzUFcQ1SCSVI3Hk=";
    static final String EXPECTED_NUBITS_SIG = "IMuzNZTZIjZjLicyDFGzqFl21vqNBGW1N5m4qHBRqbTvLBbkQeGjraeLmZEt7mRH4MSMPLFXW2T3Maz+HYx1tEc=";
    static final String EXPECTED_NUBITS_SIG_UNICODE = "Hx7xkBbboXrp96dbQrJFzm2unTGwLstjbWlKa1/N1E4LJqbwJAJR1qIvwXm6LHQFnLOzwoQA45zYNjwUEPMc8sg=";

    DeterministicSeed seed = new DeterministicSeed(MNEMONIC, null, "", 0);
    DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(checkNotNull(seed.getSeedBytes()));
    DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterKey);
    DeterministicKey rootKey = hierarchy.get(DOGE.getBip44Path(0), false, true);
    KeyParameter aesKey = new KeyParameter(AES_KEY_BYTES);
    KeyCrypter crypter = new KeyCrypterScrypt();

    WalletPocketHD pocket;

    @Before
    public void setup() {
        BriefLogFormatter.init();

        pocket = new WalletPocketHD(rootKey, DOGE, null, null);
        pocket.keys.setLookaheadSize(20);
    }

    @Test
    public void testId() throws Exception {
        assertEquals("8747886e9a77fe2a1588df3168b867648434d6d339b0b448793ae1db8f283f41", pocket.getId());
    }

    @Test
    public void testSingleAddressWallet() throws Exception {
        ECKey key = pocket.keys.getCurrentUnusedKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        BitWalletSingleKey bitWalletSingleKey = new BitWalletSingleKey(DOGE, key);
        bitWalletSingleKey.onConnection(getBlockchainConnection(DOGE));
        assertEquals(DOGE.value("10"), bitWalletSingleKey.getBalance());
    }

    @Test
    public void xpubWallet() {
        String xpub = "xpub67tVq9TLPPoaHVSiYu8mqahm3eztTPUts6JUftNq3pZF1PJwjknrTqQhjc2qRGd6vS8wANu7mLnqkiUzFZ1xZsAZoUMi8o6icMhzGCAhcSW";
        DeterministicKey key = DeterministicKey.deserializeB58(null, xpub);
        WalletPocketHD account = new WalletPocketHD(key, BTC, null, null);
        assertEquals("1KUDsEDqSBAgxubSEWszoA9xscNRRCmujM", account.getReceiveAddress().toString());
        account = new WalletPocketHD(key, NBT, null, null);
        assertEquals("BNvJUwg3BgkbQk5br1CxvHxdcDp1EC3saE", account.getReceiveAddress().toString());
    }

    @Test
    public void xpubWalletSerialized() throws Exception {
        WalletPocketHD account = new WalletPocketHD(rootKey, BTC, null, null);
        Protos.WalletPocket proto = account.toProtobuf();
        WalletPocketHD newAccount = new WalletPocketProtobufSerializer().readWallet(proto, null);
        assertEquals(account.getPublicKeySerialized(), newAccount.getPublicKeySerialized());
    }

    @Test
    public void signMessage() throws AddressMalformedException, MissingPrivateKeyException, KeyIsEncryptedException {
        WalletPocketHD pocketHD = new WalletPocketHD(rootKey, BTC, null, null);
        pocketHD.getReceiveAddress(); // Generate the first key
        SignedMessage signedMessage = new SignedMessage("1KUDsEDqSBAgxubSEWszoA9xscNRRCmujM", MESSAGE);
        pocketHD.signMessage(signedMessage, null);
        assertEquals(EXPECTED_BITCOIN_SIG, signedMessage.getSignature());

        signedMessage = new SignedMessage("1KUDsEDqSBAgxubSEWszoA9xscNRRCmujM", MESSAGE_UNICODE);
        pocketHD.signMessage(signedMessage, null);
        assertEquals(EXPECTED_BITCOIN_SIG_UNICODE, signedMessage.getSignature());

        pocketHD = new WalletPocketHD(rootKey, NBT, null, null);
        pocketHD.getReceiveAddress(); // Generate the first key
        signedMessage = new SignedMessage("BNvJUwg3BgkbQk5br1CxvHxdcDp1EC3saE", MESSAGE);
        pocketHD.signMessage(signedMessage, null);
        assertEquals(EXPECTED_NUBITS_SIG, signedMessage.getSignature());

        signedMessage = new SignedMessage("BNvJUwg3BgkbQk5br1CxvHxdcDp1EC3saE", MESSAGE_UNICODE);
        pocketHD.signMessage(signedMessage, null);
        assertEquals(EXPECTED_NUBITS_SIG_UNICODE, signedMessage.getSignature());
    }

    @Test
    public void signMessageEncrypted() throws AddressMalformedException, MissingPrivateKeyException, KeyIsEncryptedException {
        WalletPocketHD pocketHD = new WalletPocketHD(rootKey, BTC, null, null);
        pocketHD.getReceiveAddress(); // Generate the first key
        pocketHD.encrypt(crypter, aesKey);
        SignedMessage signedMessage = new SignedMessage("1KUDsEDqSBAgxubSEWszoA9xscNRRCmujM", MESSAGE);
        pocketHD.signMessage(signedMessage, aesKey);
        assertEquals(EXPECTED_BITCOIN_SIG, signedMessage.getSignature());

        signedMessage = new SignedMessage("1KUDsEDqSBAgxubSEWszoA9xscNRRCmujM", MESSAGE_UNICODE);
        pocketHD.signMessage(signedMessage, aesKey);
        assertEquals(EXPECTED_BITCOIN_SIG_UNICODE, signedMessage.getSignature());
    }

    @Test
    public void signMessageEncryptedFailed() throws AddressMalformedException, MissingPrivateKeyException, KeyIsEncryptedException {
        WalletPocketHD pocketHD = new WalletPocketHD(rootKey, BTC, null, null);
        pocketHD.getReceiveAddress(); // Generate the first key
        pocketHD.encrypt(crypter, aesKey);
        SignedMessage signedMessage = new SignedMessage("1KUDsEDqSBAgxubSEWszoA9xscNRRCmujM", MESSAGE);
        pocketHD.signMessage(signedMessage, null);
        assertEquals(SignedMessage.Status.KeyIsEncrypted, signedMessage.status);
    }

    @Test
    public void watchingAddresses() {
        List<AbstractAddress> watchingAddresses = pocket.getAddressesToWatch();
        assertEquals(40, watchingAddresses.size()); // 20 + 20 lookahead size
        for (int i = 0; i < addresses.size(); i++) {
            assertEquals(addresses.get(i), watchingAddresses.get(i).toString());
        }
    }

    @Test
    public void issuedKeys() throws Bip44KeyLookAheadExceededException {
        List<BitAddress> issuedAddresses = new ArrayList<>();
        assertEquals(0, pocket.getIssuedReceiveAddresses().size());
        assertEquals(0, pocket.keys.getNumIssuedExternalKeys());

        issuedAddresses.add(0, pocket.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS));
        BitAddress freshAddress = pocket.getFreshReceiveAddress();
        assertEquals(freshAddress, pocket.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS));
        assertEquals(1, pocket.getIssuedReceiveAddresses().size());
        assertEquals(1, pocket.keys.getNumIssuedExternalKeys());
        assertEquals(issuedAddresses, pocket.getIssuedReceiveAddresses());

        issuedAddresses.add(0, pocket.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS));
        freshAddress = pocket.getFreshReceiveAddress();
        assertEquals(freshAddress, pocket.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS));
        assertEquals(2, pocket.getIssuedReceiveAddresses().size());
        assertEquals(2, pocket.keys.getNumIssuedExternalKeys());
        assertEquals(issuedAddresses, pocket.getIssuedReceiveAddresses());
    }

    @Test
    public void issuedKeysLimit() throws Exception {
        assertTrue(pocket.canCreateFreshReceiveAddress());
        try {
            for (int i = 0; i < 100; i++) {
                pocket.getFreshReceiveAddress();
            }
        } catch (Bip44KeyLookAheadExceededException e) {
            assertFalse(pocket.canCreateFreshReceiveAddress());
            // We haven't used any key so the total must be 20 - 1 (the unused key)
            assertEquals(19, pocket.getNumberIssuedReceiveAddresses());
            assertEquals(19, pocket.getIssuedReceiveAddresses().size());
        }

        pocket.onConnection(getBlockchainConnection(DOGE));

        assertTrue(pocket.canCreateFreshReceiveAddress());
        try {
            for (int i = 0; i < 100; i++) {
                pocket.getFreshReceiveAddress();
            }
        } catch (Bip44KeyLookAheadExceededException e) {
//            try {
//                pocket.getFreshReceiveAddress();
//            } catch (Bip44KeyLookAheadExceededException e1) { /* ignore */ }
            assertFalse(pocket.canCreateFreshReceiveAddress());
            // We used 18, so the total must be (20-1)+18=37
            assertEquals(37, pocket.getNumberIssuedReceiveAddresses());
            assertEquals(37, pocket.getIssuedReceiveAddresses().size());
        }
    }

    @Test
    public void issuedKeysLimit2() throws Exception {
        assertTrue(pocket.canCreateFreshReceiveAddress());
        try {
            for (int i = 0; i < 100; i++) {
                pocket.getFreshReceiveAddress();
            }
        } catch (Bip44KeyLookAheadExceededException e) {
            assertFalse(pocket.canCreateFreshReceiveAddress());
            // We haven't used any key so the total must be 20 - 1 (the unused key)
            assertEquals(19, pocket.getNumberIssuedReceiveAddresses());
            assertEquals(19, pocket.getIssuedReceiveAddresses().size());
        }
    }

    @Test
    public void usedAddresses() throws Exception {
        assertEquals(0, pocket.getUsedAddresses().size());

        pocket.onConnection(getBlockchainConnection(DOGE));

        // Receive and change addresses
        assertEquals(13, pocket.getUsedAddresses().size());
    }

    private Sha256Hash send(Value value, WalletPocketHD w1, WalletPocketHD w2) throws Exception {
        assertEquals(w1.getCoinType(), w2.getCoinType());
        CoinType type = w1.getCoinType();
        BitSendRequest req = w1.sendCoinsOffline(w2.getReceiveAddress(), value);
        req.feePerTxSize = type.value("0.01");
        w1.completeAndSignTx(req);
        byte[] txBytes = req.tx.bitcoinSerialize();
        w1.addNewTransactionIfNeeded(new BitTransaction(type, txBytes));
        w2.addNewTransactionIfNeeded(new BitTransaction(type, txBytes));

        return req.tx.getHash();
    }

    @Test
    public void testSendingAndBalances() throws Exception {
        DeterministicHierarchy h = new DeterministicHierarchy(masterKey);
        WalletPocketHD account1 = new WalletPocketHD(h.get(BTC.getBip44Path(0), false, true), BTC, null, null);
        WalletPocketHD account2 = new WalletPocketHD(h.get(BTC.getBip44Path(1), false, true), BTC, null, null);
        WalletPocketHD account3 = new WalletPocketHD(h.get(BTC.getBip44Path(2), false, true), BTC, null, null);

        Transaction tx = new Transaction(BTC);
        tx.addOutput(BTC.oneCoin().toCoin(), account1.getReceiveAddress());
        tx.getConfidence().setSource(Source.SELF);
        account1.addNewTransactionIfNeeded(tx);


        assertEquals(BTC.value("1"), account1.getBalance());
        assertEquals(BTC.value("0"), account2.getBalance());
        assertEquals(BTC.value("0"), account3.getBalance());

        Sha256Hash txId = send(BTC.value("0.05"), account1, account2);

        assertEquals(BTC.value("0.94"), account1.getBalance());
        assertEquals(BTC.value("0"), account2.getBalance());
        assertEquals(BTC.value("0"), account3.getBalance());

        setTrustedTransaction(account1, txId);
        setTrustedTransaction(account2, txId);

        assertEquals(BTC.value("0.94"), account1.getBalance());
        assertEquals(BTC.value("0.05"), account2.getBalance());

        txId = send(BTC.value("0.07"), account1, account3);
        setTrustedTransaction(account1, txId);
        setTrustedTransaction(account3, txId);

        assertEquals(BTC.value("0.86"), account1.getBalance());
        assertEquals(BTC.value("0.05"), account2.getBalance());
        assertEquals(BTC.value("0.07"), account3.getBalance());

        txId = send(BTC.value("0.03"), account2, account3);
        setTrustedTransaction(account2, txId);
        setTrustedTransaction(account3, txId);

        assertEquals(BTC.value("0.86"), account1.getBalance());
        assertEquals(BTC.value("0.01"), account2.getBalance());
        assertEquals(BTC.value("0.1"), account3.getBalance());
    }

    private void setTrustedTransaction(WalletPocketHD account, Sha256Hash txId) {
        BitTransaction tx = checkNotNull(account.getTransaction(txId));
        tx.setSource(Source.SELF);
    }

    @Test
    public void testLoading() throws Exception {
        assertFalse(pocket.isLoading());
        pocket.onConnection(getBlockchainConnection(DOGE));
        // TODO add fine grained control to the blockchain connection in order to test the loading status
        assertFalse(pocket.isLoading());
    }

    @Test
    public void fillTransactions() throws Exception {
        pocket.onConnection(getBlockchainConnection(DOGE));

        checkUnspentOutputs(getDummyUtxoSet(), pocket);
        assertEquals(11000000000L, pocket.getBalance().value);

        // Issued keys
        assertEquals(18, pocket.keys.getNumIssuedExternalKeys());
        assertEquals(9, pocket.keys.getNumIssuedInternalKeys());

        // No addresses left to subscribe
        List<AbstractAddress> addressesToWatch = pocket.getAddressesToWatch();
        assertEquals(0, addressesToWatch.size());

        // 18 external issued + 20 lookahead +  9 external issued + 20 lookahead
        assertEquals(67, pocket.addressesStatus.size());
        assertEquals(67, pocket.addressesSubscribed.size());

        BitAddress receiveAddr = pocket.getReceiveAddress();
        // This key is not issued
        assertEquals(18, pocket.keys.getNumIssuedExternalKeys());
        assertEquals(67, pocket.addressesStatus.size());
        assertEquals(67, pocket.addressesSubscribed.size());

        DeterministicKey key = pocket.keys.findKeyFromPubHash(receiveAddr.getHash160());
        assertNotNull(key);
        // 18 here is the key index, not issued keys count
        assertEquals(18, key.getChildNumber().num());

        assertEquals(11000000000L, pocket.getBalance().value);
    }

    @Test
    public void serializeTransactionsBtc() throws Exception, Bip44KeyLookAheadExceededException {
        WalletPocketHD account = new WalletPocketHD(rootKey, BTC, null, null);
        Transaction tx = new Transaction(BTC);
        tx.addOutput(BTC.oneCoin().toCoin(), account.getReceiveAddress());
        account.addNewTransactionIfNeeded(tx);
        testWalletSerializationForCoin(account);
    }

    @Test
    public void serializeTransactionsNbt() throws Exception, Bip44KeyLookAheadExceededException {
        WalletPocketHD account = new WalletPocketHD(rootKey, NBT, null, null);
        Transaction tx = new Transaction(NBT);
        tx.addOutput(NBT.oneCoin().toCoin(), account.getReceiveAddress());
        account.addNewTransactionIfNeeded(tx);
        testWalletSerializationForCoin(account);
    }

    @Test
    public void serializeTransactionsVpn() throws Exception, Bip44KeyLookAheadExceededException {
        WalletPocketHD account = new WalletPocketHD(rootKey, VPN, null, null);
        // Test tx with null extra bytes
        Transaction tx = new Transaction(VPN);
        tx.setTime(0x99999999);
        tx.addOutput(VPN.oneCoin().toCoin(), account.getFreshReceiveAddress());
        account.addNewTransactionIfNeeded(tx);
        WalletPocketHD newAccount = testWalletSerializationForCoin(account);
        Transaction newTx = newAccount.getRawTransaction(tx.getHash());
        assertNotNull(newTx);
        assertNotNull(newTx.getExtraBytes());
        assertEquals(0, newTx.getExtraBytes().length);
        // Test tx with empty extra bytes
        tx = new Transaction(VPN);
        tx.setTime(0x99999999);
        tx.setExtraBytes(new byte[0]);
        tx.addOutput(VPN.oneCoin().toCoin(), account.getFreshReceiveAddress());
        account.addNewTransactionIfNeeded(tx);
        newAccount = testWalletSerializationForCoin(account);
        newTx = newAccount.getRawTransaction(tx.getHash());
        assertNotNull(newTx);
        assertNotNull(newTx.getExtraBytes());
        assertEquals(0, newTx.getExtraBytes().length);
        // Test tx with extra bytes
        tx = new Transaction(VPN);
        tx.setTime(0x99999999);
        byte[] bytes = {0x1, 0x2, 0x3};
        tx.setExtraBytes(bytes);
        tx.addOutput(VPN.oneCoin().toCoin(), account.getFreshReceiveAddress());
        account.addNewTransactionIfNeeded(tx);
        newAccount = testWalletSerializationForCoin(account);
        newTx = newAccount.getRawTransaction(tx.getHash());
        assertNotNull(newTx);
        assertArrayEquals(bytes, newTx.getExtraBytes());
    }

    private WalletPocketHD testWalletSerializationForCoin(WalletPocketHD account) throws UnreadableWalletException {
        Protos.WalletPocket proto = account.toProtobuf();
        WalletPocketHD newAccount = new WalletPocketProtobufSerializer().readWallet(proto, null);
        assertEquals(account.getBalance().value, newAccount.getBalance().value);
        Map<Sha256Hash, BitTransaction> transactions = account.getTransactions();
        Map<Sha256Hash, BitTransaction> newTransactions = newAccount.getTransactions();
        for (Sha256Hash txId : transactions.keySet()) {
            assertTrue(newTransactions.containsKey(txId));
            assertEquals(transactions.get(txId), newTransactions.get(txId));
        }
        return newAccount;
    }

    @Test
    public void serializeUnencryptedNormal() throws Exception {
        pocket.onConnection(getBlockchainConnection(DOGE));

        Protos.WalletPocket walletPocketProto = pocket.toProtobuf();
        System.out.println(walletPocketProto.toString());

        WalletPocketHD newPocket = new WalletPocketProtobufSerializer().readWallet(walletPocketProto, null);

        assertEquals(pocket.getBalance().value, newPocket.getBalance().value);
        assertEquals(DOGE.value(11000000000l), newPocket.getBalance());

        Map<TrimmedOutPoint, OutPointOutput> expectedUtxoSet = getDummyUtxoSet();
        checkUnspentOutputs(expectedUtxoSet, pocket);
        checkUnspentOutputs(expectedUtxoSet, newPocket);

        assertEquals(pocket.getCoinType(), newPocket.getCoinType());
        assertEquals(pocket.getDescriptionOrCoinName(), newPocket.getDescriptionOrCoinName());
        assertEquals(pocket.keys.toProtobuf().toString(), newPocket.keys.toProtobuf().toString());
        assertEquals(pocket.getLastBlockSeenHash(), newPocket.getLastBlockSeenHash());
        assertEquals(pocket.getLastBlockSeenHeight(), newPocket.getLastBlockSeenHeight());
        assertEquals(pocket.getLastBlockSeenTimeSecs(), newPocket.getLastBlockSeenTimeSecs());

        for (BitTransaction tx : pocket.getTransactions().values()) {
            assertEquals(tx, newPocket.getTransaction(tx.getHash()));
            BitTransaction txNew = checkNotNull(newPocket.getTransaction(tx.getHash()));
            assertInputsEquals(tx.getInputs(), txNew.getInputs());
            assertOutputsEquals(tx.getOutputs(false), txNew.getOutputs(false));
        }

        for (AddressStatus status : pocket.getAllAddressStatus()) {
            if (status.getStatus() == null) continue;
            assertEquals(status, newPocket.getAddressStatus(status.getAddress()));
        }

        // Issued keys
        assertEquals(18, newPocket.keys.getNumIssuedExternalKeys());
        assertEquals(9, newPocket.keys.getNumIssuedInternalKeys());

        newPocket.onConnection(getBlockchainConnection(DOGE));

        // No addresses left to subscribe
        List<AbstractAddress> addressesToWatch = newPocket.getAddressesToWatch();
        assertEquals(0, addressesToWatch.size());

        // 18 external issued + 20 lookahead +  9 external issued + 20 lookahead
        assertEquals(67, newPocket.addressesStatus.size());
        assertEquals(67, newPocket.addressesSubscribed.size());
    }

    private void assertInputsEquals(List<TransactionInput> expected, List<TransactionInput> actual) {
        assertEquals(expected.size(), actual.size());

        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i).bitcoinSerialize(), actual.get(i).bitcoinSerialize());
        }
    }

    private void assertOutputsEquals(List<TransactionOutput> expected, List<TransactionOutput> actual) {
        assertEquals(expected.size(), actual.size());

        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.get(i).bitcoinSerialize(), actual.get(i).bitcoinSerialize());
        }
    }

    @Test
    public void serializeUnencryptedEmpty() throws Exception {
        pocket.maybeInitializeAllKeys();
        Protos.WalletPocket walletPocketProto = pocket.toProtobuf();

        WalletPocketHD newPocket = new WalletPocketProtobufSerializer().readWallet(walletPocketProto, null);

        assertEquals(walletPocketProto.toString(), newPocket.toProtobuf().toString());

        // Issued keys
        assertEquals(0, newPocket.keys.getNumIssuedExternalKeys());
        assertEquals(0, newPocket.keys.getNumIssuedInternalKeys());

        // 20 lookahead + 20 lookahead
        assertEquals(40, newPocket.keys.getActiveKeys().size());
    }


    @Test
    public void serializeEncryptedEmpty() throws Exception {
        pocket.maybeInitializeAllKeys();
        pocket.encrypt(crypter, aesKey);

        Protos.WalletPocket walletPocketProto = pocket.toProtobuf();

        WalletPocketHD newPocket = new WalletPocketProtobufSerializer().readWallet(walletPocketProto, crypter);

        assertEquals(walletPocketProto.toString(), newPocket.toProtobuf().toString());

        pocket.decrypt(aesKey);

        // One is encrypted, so they should not match
        assertNotEquals(pocket.toProtobuf().toString(), newPocket.toProtobuf().toString());

        newPocket.decrypt(aesKey);

        assertEquals(pocket.toProtobuf().toString(), newPocket.toProtobuf().toString());
    }


    @Test
    public void serializeEncryptedNormal() throws Exception {
        pocket.maybeInitializeAllKeys();
        pocket.encrypt(crypter, aesKey);
        pocket.onConnection(getBlockchainConnection(DOGE));

        assertEquals(DOGE.value(11000000000l), pocket.getBalance());

        assertAllKeysEncrypted(pocket);

        WalletPocketHD newPocket = new WalletPocketProtobufSerializer().readWallet(pocket.toProtobuf(), crypter);

        assertEquals(DOGE.value(11000000000l), newPocket.getBalance());

        Map<TrimmedOutPoint, OutPointOutput> expectedUtxoSet = getDummyUtxoSet();
        checkUnspentOutputs(expectedUtxoSet, pocket);
        checkUnspentOutputs(expectedUtxoSet, newPocket);

        assertAllKeysEncrypted(newPocket);

        pocket.decrypt(aesKey);
        newPocket.decrypt(aesKey);

        assertAllKeysDecrypted(pocket);
        assertAllKeysDecrypted(newPocket);
    }

    private void checkUnspentOutputs(Map<TrimmedOutPoint, OutPointOutput> expectedUtxoSet, WalletPocketHD pocket) {
        Map<TrimmedOutPoint, OutPointOutput> pocketUtxoSet = pocket.getUnspentOutputs(false);
        for (OutPointOutput utxo : expectedUtxoSet.values()) {
            assertEquals(utxo, pocketUtxoSet.get(utxo.getOutPoint()));
        }
    }

    private void assertAllKeysDecrypted(WalletPocketHD pocket) {
        List<ECKey> keys = pocket.keys.getKeys(false);
        for (ECKey k : keys) {
            DeterministicKey key = (DeterministicKey) k;

            assertFalse(key.isEncrypted());
        }
    }

    private void assertAllKeysEncrypted(WalletPocketHD pocket) {
        List<ECKey> keys = pocket.keys.getKeys(false);
        for (ECKey k : keys) {
            DeterministicKey key = (DeterministicKey) k;

            assertTrue(key.isEncrypted());
        }
    }

    @Test
    public void createDustTransactionFee() throws Exception {
        pocket.onConnection(getBlockchainConnection(DOGE));

        BitAddress toAddr = BitAddress.from(DOGE, "nZB8PHkfgJvuCJqchSexmfY3ABXa2aE1vp");

        Value softDust = DOGE.getSoftDustLimit();
        assertNotNull(softDust);
        // Send a soft dust
        BitSendRequest sendRequest = pocket.sendCoinsOffline(toAddr, softDust.subtract(DOGE.value(1)));
        pocket.completeTransaction(sendRequest);
        assertEquals(DOGE.getFeeValue().multiply(2), sendRequest.tx.getFee());
    }

    @Test
    public void createTransactionAndBroadcast() throws Exception {
        pocket.onConnection(getBlockchainConnection(DOGE));

        BitAddress toAddr = BitAddress.from(DOGE, "nZB8PHkfgJvuCJqchSexmfY3ABXa2aE1vp");

        Value orgBalance = pocket.getBalance();
        BitSendRequest sendRequest = pocket.sendCoinsOffline(toAddr, DOGE.value(AMOUNT_TO_SEND));
        sendRequest.shuffleOutputs = false;
        sendRequest.feePerTxSize = DOGE.value(0);
        pocket.completeTransaction(sendRequest);

        // FIXME, mock does not work here
//        pocket.broadcastTx(tx);
        pocket.addNewTransactionIfNeeded(sendRequest.tx);
        assertEquals(orgBalance.subtract(AMOUNT_TO_SEND), pocket.getBalance());
    }



    // Util methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public class MessageComparator implements Comparator<ChildMessage> {
        @Override
        public int compare(ChildMessage o1, ChildMessage o2) {
            String s1 = Utils.HEX.encode(o1.bitcoinSerialize());
            String s2 = Utils.HEX.encode(o2.bitcoinSerialize());
            return s1.compareTo(s2);
        }
    }

    HashMap<AbstractAddress, AddressStatus> getDummyStatuses() throws AddressMalformedException {
        HashMap<AbstractAddress, AddressStatus> status = new HashMap<>(40);

        for (int i = 0; i < addresses.size(); i++) {
            AbstractAddress address = DOGE.newAddress(addresses.get(i));
            status.put(address, new AddressStatus(address, statuses[i]));
        }

        return status;
    }

    private HashMap<AbstractAddress, ArrayList<HistoryTx>> getDummyHistoryTXs() throws AddressMalformedException, JSONException {
        HashMap<AbstractAddress, ArrayList<HistoryTx>> htxs = new HashMap<>(40);

        for (int i = 0; i < statuses.length; i++) {
            JSONArray jsonArray = new JSONArray(history[i]);
            ArrayList<HistoryTx> list = new ArrayList<>();
            for (int j = 0; j < jsonArray.length(); j++) {
                list.add(new HistoryTx(jsonArray.getJSONObject(j)));
            }
            htxs.put(DOGE.newAddress(addresses.get(i)), list);
        }

        return htxs;
    }

    private HashMap<AbstractAddress, ArrayList<UnspentTx>> getDummyUnspentTXs() throws AddressMalformedException, JSONException {
        HashMap<AbstractAddress, ArrayList<UnspentTx>> utxs = new HashMap<>(40);

        for (int i = 0; i < statuses.length; i++) {
            JSONArray jsonArray = new JSONArray(unspent[i]);
            ArrayList<UnspentTx> list = new ArrayList<>();
            for (int j = 0; j < jsonArray.length(); j++) {
                list.add(new UnspentTx(jsonArray.getJSONObject(j)));
            }
            utxs.put(DOGE.newAddress(addresses.get(i)), list);
        }

        return utxs;
    }

    private Map<TrimmedOutPoint, OutPointOutput> getDummyUtxoSet() throws AddressMalformedException, JSONException {
        final HashMap<TrimmedOutPoint, OutPointOutput> unspentOutputs = new HashMap<>();

        for (int i = 0; i < statuses.length; i++) {
            BitAddress address = (BitAddress) DOGE.newAddress(addresses.get(i));
            JSONArray utxoArray = new JSONArray(unspent[i]);

            for (int j = 0; j < utxoArray.length(); j++) {
                JSONObject utxoObject = utxoArray.getJSONObject(j);
                //"[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 11, \"value\": 500000000, \"height\": 160267}]",
                TrimmedOutPoint outPoint = new TrimmedOutPoint(DOGE, utxoObject.getInt("tx_pos"),
                        new Sha256Hash(utxoObject.getString("tx_hash")));
                TransactionOutput output = new TransactionOutput(DOGE, null,
                        Coin.valueOf(utxoObject.getLong("value")), address);
                OutPointOutput utxo = new OutPointOutput(outPoint, output, false);
                unspentOutputs.put(outPoint, utxo);
            }
        }

        return unspentOutputs;
    }

    private HashMap<Sha256Hash, byte[]> getDummyRawTXs() throws AddressMalformedException, JSONException {
        HashMap<Sha256Hash, byte[]> rawTxs = new HashMap<>();

        for (String[] tx : txs) {
            rawTxs.put(new Sha256Hash(tx[0]), Utils.HEX.decode(tx[1]));
        }

        return rawTxs;
    }

    class MockBlockchainConnection implements BitBlockchainConnection {
        final HashMap<AbstractAddress, AddressStatus> statuses;
        final HashMap<AbstractAddress, ArrayList<HistoryTx>> historyTxs;
        final HashMap<AbstractAddress, ArrayList<UnspentTx>> unspentTxs;
        final HashMap<Sha256Hash, byte[]> rawTxs;
        private CoinType coinType;

        MockBlockchainConnection(CoinType coinType) throws Exception {
            this.coinType = coinType;
            statuses = getDummyStatuses();
            historyTxs = getDummyHistoryTXs();
            unspentTxs = getDummyUnspentTXs();
            rawTxs = getDummyRawTXs();
        }

        @Override
        public void getBlock(int height, TransactionEventListener<BitTransaction> listener) { }

        @Override
        public void subscribeToBlockchain(TransactionEventListener<BitTransaction> listener) {
            listener.onNewBlock(new BlockHeader(coinType, blockTimestamp, blockHeight));
        }

        @Override
        public void subscribeToAddresses(List<AbstractAddress> addresses, TransactionEventListener<BitTransaction> listener) {
            for (AbstractAddress a : addresses) {
                AddressStatus status = statuses.get(a);
                if (status == null) {
                    status = new AddressStatus(a, null);
                }
                listener.onAddressStatusUpdate(status);
            }
        }

        @Override
        public void getHistoryTx(AddressStatus status, TransactionEventListener<BitTransaction> listener) {
            List<HistoryTx> htx = historyTxs.get(status.getAddress());
            if (htx == null) {
                htx = ImmutableList.of();
            }
            listener.onTransactionHistory(status, htx);
        }

        @Override
        public void getUnspentTx(AddressStatus status, BitTransactionEventListener listener) {
            List<UnspentTx> utx = unspentTxs.get(status.getAddress());
            if (utx == null) {
                utx = ImmutableList.of();
            }
            listener.onUnspentTransactionUpdate(status, utx);
        }

        @Override
        public void getTransaction(Sha256Hash txHash, TransactionEventListener<BitTransaction> listener) {
            BitTransaction tx = new BitTransaction(coinType, rawTxs.get(txHash));
            listener.onTransactionUpdate(tx);
        }

        @Override
        public void broadcastTx(BitTransaction tx, TransactionEventListener<BitTransaction> listener) {
//            List<AddressStatus> newStatuses = new ArrayList<AddressStatus>();
//            Random rand = new Random();
//            byte[] randBytes = new byte[32];
//            // Get spent outputs and modify statuses
//            for (TransactionInput txi : tx.getInputs()) {
//                UnspentTx unspentTx = new UnspentTx(
//                        txi.getOutpoint(), txi.getValue().value, 0);
//
//                for (Map.Entry<Address, ArrayList<UnspentTx>> entry : utxs.entrySet()) {
//                    if (entry.getValue().remove(unspentTx)) {
//                        rand.nextBytes(randBytes);
//                        AddressStatus newStatus = new AddressStatus(entry.getKey(), Utils.HEX.encode(randBytes));
//                        statuses.put(entry.getKey(), newStatus);
//                        newStatuses.add(newStatus);
//                    }
//                }
//            }
//
//            for (TransactionOutput txo : tx.getOutputs()) {
//                if (txo.getAddressFromP2PKHScript(coinType) != null) {
//                    Address address = txo.getAddressFromP2PKHScript(coinType);
//                    if (addresses.contains(address.toString())) {
//                        AddressStatus newStatus = new AddressStatus(address, tx.getHashAsString());
//                        statuses.put(address, newStatus);
//                        newStatuses.add(newStatus);
//                        if (!utxs.containsKey(address)) {
//                            utxs.put(address, new ArrayList<UnspentTx>());
//                        }
//                        ArrayList<UnspentTx> unspentTxs = utxs.get(address);
//                        unspentTxs.add(new UnspentTx(txo.getOutPointFor(),
//                                txo.getValue().value, 0));
//                        if (!historyTxs.containsKey(address)) {
//                            historyTxs.put(address, new ArrayList<HistoryTx>());
//                        }
//                        ArrayList<HistoryTx> historyTxes = historyTxs.get(address);
//                        historyTxes.add(new HistoryTx(txo.getOutPointFor(), 0));
//                    }
//                }
//            }
//
//            rawTxs.put(tx.getHash(), tx.bitcoinSerialize());
//
//            for (AddressStatus newStatus : newStatuses) {
//                listener.onAddressStatusUpdate(newStatus);
//            }
        }

        @Override
        public boolean broadcastTxSync(BitTransaction tx) {
            return false;
        }

        @Override
        public void ping(String versionString) {}

        @Override
        public void addEventListener(ConnectionEventListener listener) {

        }

        @Override
        public void resetConnection() {

        }

        @Override
        public void stopAsync() {

        }

        @Override
        public boolean isActivelyConnected() {
            return false;
        }

        @Override
        public void startAsync() {

        }
    }

    private MockBlockchainConnection getBlockchainConnection(CoinType coinType) throws Exception {
        return new MockBlockchainConnection(coinType);
    }

    // Mock data
    long blockTimestamp = 1411000000l;
    int blockHeight = 200000;

    List<String> addresses = ImmutableList.of(
            "nnfP8VuPfZXhNtMDzvX1bKurzcV1k7HNrQ",
            "nf4AUKiaGdx4GTbbh222KvtuCbAuvbcdE2",
            "npGkmbWtdWybFNSZQXUK6zZxbEocMtrTzz",
            "nVaN45bbs6AUc1EUpP71hHGBGb2qciNrJc",
            "nrdHFZP1AfdKBrjsSQmwFm8R2i2mzMef75",
            "niGZgfbhFYn6tJqksmC8CnzSRL1GHNsu7e",
            "nh6w8yu1zoKYoT837ffkVmuPjTaP69Pc5E",
            "nbyMgmEghsL9tpk7XfdH9gLGudh6Lrbbuf",
            "naX9akzYuWY1gKbcZo3t36aBKc1gqbzgSs",
            "nqcPVTGeAfCowELB2D5PdVF3FWFjFtkkFf",
            "nd4vVvPTpp2LfcsMPsmG3Dh7MFAsqRHp4g",
            "nVK4Uz5Sf56ygrr6RiwXcRvH8AuUVbjjHi",
            "nbipkRwT1NCSXZSrm8uAAEgQAA2s2NWMkG",
            "nZU6QAMAdCQcVDxEmL7GEY6ykFm8m6u6am",
            "nbFqayssM5s7hLjLFwf2d95JXKRWBk2pBH",
            "nacZfcNgVi47hamLscWdUGQhUQGGpSdgq8",
            "niiMT7ZYeFnaLPYtqQFBBX1sP3dT5JtcEw",
            "ns6GyTaniLWaiYrvAf5tk4D8tWrbcCLGyj",
            "nhdwQfptLTzLJGbLF3vvtqyBaqPMPecDmE",
            "neMUAGeTzwxLbSkXMsdmWf1fTKS1UsJjXY",
            "nXsAMeXxtpx8jaxnU3Xv9ZQ6ZcRcD1xYhR",
            "ns35rKnnWf6xP3KSj5WPkMCVVaADGi6Ndk",
            "nk4wcXYBEWs5HkhNLsuaQJoAjJHoK6SQmG",
            "npsJQWu8fsALoTPum8D4j8FDkyeusk8fU8",
            "nZNhZo4Vz3DnwQGPL3SJTZgUw2Kh4g9oay",
            "nnxDTYB8WMHMgLnCY2tuaEtysvdhRnWehZ",
            "nb2iaDXG1EqkcE47H87WQFkQNkeVK66Z21",
            "nWHAkpn2DB3DJRsbiK3vpbigoa3M2uVuF8",
            "nViKdC7Gm6TMmCuWTBwVE9i4rJhyfwbfqg",
            "nZQV5BifbGPzaxTrB4efgHruWH5rufemqP",
            "nVvZaLvnkCVAzpLYPoHeqU4w9zJ5yrZgUn",
            "nrMp6pRCk98WYWkCWq9Pqthz9HbpQu8BT3",
            "nnA3aYsLqysKT6gAu1dr4EKm586cmKiRxS",
            "nVfcVgMY7DL6nqoSxwJy7B7hKXirQwj6ic",
            "ni4oAzi6nCVuEdjoHyVMVKWb1DqTd3qY3H",
            "nnpf3gx442yomniRJPMGPapgjHrraPZXxJ",
            "nkuFnF8wUmHFkMycaFMvyjBoiMeR5KBKGd",
            "nXKccwjaUyrQkLrdqKT6aq6cDiFgBBVgNz",
            "nZMSNsXSAL7i1YD6KP5FrhATuZ2CWvnxqR",
            "nUEkQ3LjH9m4ScbP6NGtnAdnnUsdtWv99Q"
    );

    String[] statuses = {
            "fe7c109d8bd90551a406cf0b3499117db04bc9c4f48e1df27ac1cf3ddcb3d464",
            "8a53babd831c6c3a857e20190e884efe75a005bdd7cd273c4f27ab1b8ec81c2d",
            "86bc2f0cf0112fd59c9aadfe5c887062c21d7a873db260dff68dcfe4417fe212",
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    };


    String[] unspent = {
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 0, \"value\": 500000000, \"height\": 160267}, {\"tx_hash\": \"89a72ba4732505ce9b09c30668db985952701252ce0adbd7c43336396697d6ae\", \"tx_pos\": 0, \"value\": 500000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 1, \"value\": 1000000000, \"height\": 160267}, {\"tx_hash\": \"edaf445288d8e65cf7963bc8047c90f53681acaadc5ccfc5ecc67aedbd73cddb\", \"tx_pos\": 0, \"value\": 500000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 2, \"value\": 500000000, \"height\": 160267}, {\"tx_hash\": \"81a1f0f8242d5e71e65ff9e8ec51e8e85d641b607d7f691c1770d4f25918ebd7\", \"tx_pos\": 0, \"value\": 1000000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 3, \"value\": 500000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 4, \"value\": 1000000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 11, \"value\": 500000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 12, \"value\": 1000000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 13, \"value\": 500000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 6, \"value\": 500000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 7, \"value\": 1000000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 8, \"value\": 500000000, \"height\": 160267}]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 9, \"value\": 500000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 10, \"value\": 1000000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]"
    };

    String[] history = {
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}, {\"tx_hash\": \"89a72ba4732505ce9b09c30668db985952701252ce0adbd7c43336396697d6ae\", \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}, {\"tx_hash\": \"edaf445288d8e65cf7963bc8047c90f53681acaadc5ccfc5ecc67aedbd73cddb\", \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}, {\"tx_hash\": \"81a1f0f8242d5e71e65ff9e8ec51e8e85d641b607d7f691c1770d4f25918ebd7\", \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]"
    };

    String[][] txs = {
            {"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f", "0100000001b8778dff640ccb144346d9db48201639b2707a0cc59e19672d2dd76cc6d1a5a6010000006b48304502210098d2e5b8a6c72442430bc09f2f4bcb56612c5b9e5eee821d65b412d099bb723402204f7f008ac052e5d7be0ab5b0c85ea5e627d725a521bd9e9b193d1fdf81c317a0012102d26e423c9da9ff4a7bf6b756b2dafb75cca34fbd34f64c4c3b77c37179c5bba2ffffffff0e0065cd1d000000001976a914ca983c2da690de3cdc693ca013d93e569810c52c88ac00ca9a3b000000001976a91477263ab93a49b1d3eb5887187704cdb82e1c60ce88ac0065cd1d000000001976a914dc40fbbc8caa1f7617d275aec9a3a14ce8d8652188ac0065cd1d000000001976a9140f2b1e5b376e22c5d1e635233eb90cf50ad9095188ac00ca9a3b000000001976a914f612ffd50b6a69df5e34ee0c5b529dfaaedca03d88ac00f633bce60000001976a914937258e3a8c463ec07e78ce62326c488170ad25e88ac0065cd1d000000001976a9142848ad5ff4cc32df3649646017c47bc04e8d211788ac00ca9a3b000000001976a914fa937737a6df2b8a5e39cce8b0bdd9510683023a88ac0065cd1d000000001976a914ae248a5d710143b6b309aaab9ce17059536e2aa388ac0065cd1d000000001976a91438d6eb11eca405be497a7183c50e437851425e0088ac00ca9a3b000000001976a91410ac6b2704146b50a1dd8f6df70736856ebf8b3488ac0065cd1d000000001976a914456816dccb3a4f33ae2dbd3ba623997075d5c73d88ac00ca9a3b000000001976a91452957c44e5bee9a60402a739fc8959c2850ea98488ac0065cd1d000000001976a914fb2dffa402d05f74335d137e21e41d8920c745fb88ac00000000"},
            {"89a72ba4732505ce9b09c30668db985952701252ce0adbd7c43336396697d6ae", "01000000011a656d67706db286d1e6fad57eb4f411cb14f8880cea8348da339b9d434a5ec7050000006a47304402201d69fddb269b53aa742ff6437a45adb4ca5c59f666c9b4eabc4a0c7a6e6f4c0f022015a747b7a6d9371a4020f5b396dcd094b0f36af3fc82e95091da856181912dfa012102c9a8d5b2f768afe30ee772d185e7a61f751be05649a79508b38a2be8824adec3ffffffff020065cd1d000000001976a914ca983c2da690de3cdc693ca013d93e569810c52c88ac00b07098e60000001976a9141630d812e219e6bcbe494eb96f7a7900c216ad5d88ac00000000"},
            {"edaf445288d8e65cf7963bc8047c90f53681acaadc5ccfc5ecc67aedbd73cddb", "010000000164a3990893c012b20287d43d1071ac26f4b93648ff4213db6da6979beed6b7dc010000006b48304502210086ac11d4a8146b4176a72059960690c72a9776468cd671fd07c064b51f24961d02205bcf008d6995014f3cfd79100ee9beab5688c88cca15c5cea38b769563785d900121036530415a7b3b9c5976f26a63a57d119ab39491762121723c773399a2531a1bd7ffffffff020065cd1d000000001976a91477263ab93a49b1d3eb5887187704cdb82e1c60ce88ac006aad74e60000001976a914e5616848352c328c9f61b167eb1b0fde39b5cb6788ac00000000"},
            {"81a1f0f8242d5e71e65ff9e8ec51e8e85d641b607d7f691c1770d4f25918ebd7", "010000000141c217dfea3a1d8d6a06e9d3daf75b292581f652256d73a7891e5dc9c7ee3cca000000006a47304402205cce451228f98fece9645052546b82c2b2d425a4889b03999001fababfc7f4690220583b2189faef07d6b0191c788301cfab1b3f47ffe2c403d632b92c6dde27e14f012102d26e423c9da9ff4a7bf6b756b2dafb75cca34fbd34f64c4c3b77c37179c5bba2ffffffff0100ca9a3b000000001976a914dc40fbbc8caa1f7617d275aec9a3a14ce8d8652188ac00000000"}
    };
}