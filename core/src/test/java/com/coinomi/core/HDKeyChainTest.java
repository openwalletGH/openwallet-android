package com.coinomi.core;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.protos.Protos;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.crypto.DeterministicHierarchy;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.HDKeyDerivation;
import com.google.bitcoin.params.UnitTestParams;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.utils.BriefLogFormatter;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.AbstractKeyChainEventListener;
import com.google.bitcoin.wallet.DeterministicSeed;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Giannis Dzegoutanis
 */

/**
 * @author Giannis Dzegoutanis
 */
public class HDKeyChainTest {

    private HDKeyChain chain;
    private DeterministicKey masterKey;
    private final byte[] ENTROPY = Sha256Hash.create("don't use a string seed like this in real life".getBytes()).getBytes();

    @Before
    public void setup() {
        BriefLogFormatter.init();
        // You should use a random seed instead. The secs constant comes from the unit test file, so we can compare
        // serialized data properly.
        long secs = 1389353062L;


        DeterministicSeed seed = new DeterministicSeed(ENTROPY, "", secs);
        masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        masterKey.setCreationTimeSeconds(secs);
        chain = new HDKeyChain(masterKey);
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Test
    public void derive() throws Exception {
        ECKey key1 = chain.getKey(HDKeyChain.KeyPurpose.RECEIVE_FUNDS);
        ECKey key2 = chain.getKey(HDKeyChain.KeyPurpose.RECEIVE_FUNDS);

        final Address address = new Address(UnitTestParams.get(), "n1bQNoEx8uhmCzzA5JPG6sFdtsUQhwiQJV");
        assertEquals(address, key1.toAddress(UnitTestParams.get()));
        assertEquals("mnHUcqUVvrfi5kAaXJDQzBb9HsWs78b42R", key2.toAddress(UnitTestParams.get()).toString());
        assertEquals(key1, chain.findKeyFromPubHash(address.getHash160()));
        assertEquals(key2, chain.findKeyFromPubKey(key2.getPubKey()));

        key1.sign(Sha256Hash.ZERO_HASH);

        ECKey key3 = chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        assertEquals("mqumHgVDqNzuXNrszBmi7A2UpmwaPMx4HQ", key3.toAddress(UnitTestParams.get()).toString());
        key3.sign(Sha256Hash.ZERO_HASH);
    }

    @Test
    public void deriveCoin() throws Exception {
        DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterKey);
        DeterministicKey rootKey = hierarchy.get(BitcoinMain.get().getBip44Path(0), false, true);
        chain = new HDKeyChain(rootKey);

        ECKey key1 = chain.getKey(HDKeyChain.KeyPurpose.RECEIVE_FUNDS);
        ECKey key2 = chain.getKey(HDKeyChain.KeyPurpose.RECEIVE_FUNDS);

        final Address address = new Address(BitcoinMain.get(), "1Q3gb4JDYszDdq2vbwYnjfjw9QpSQzWDn2");
        assertEquals(address, key1.toAddress(BitcoinMain.get()));
        assertEquals("15yXWFMXsiaLoXq2AhmEsdpPX7E2y2wwXj", key2.toAddress(BitcoinMain.get()).toString());
        assertEquals(key1, chain.findKeyFromPubHash(address.getHash160()));
        assertEquals(key2, chain.findKeyFromPubKey(key2.getPubKey()));

        key1.sign(Sha256Hash.ZERO_HASH);

        ECKey key3 = chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        assertEquals("1BZDuurD5DZ4T56pUXShiREkKz6S1dC3aU", key3.toAddress(BitcoinMain.get()).toString());
        key3.sign(Sha256Hash.ZERO_HASH);

        ECKey key4 = chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        assertEquals("18Ro4ucrdHfW3rDHmsmL4QuDD86EswExab", key4.toAddress(BitcoinMain.get()).toString());
        key4.sign(Sha256Hash.ZERO_HASH);
    }


    @Test
    public void events() throws Exception {
        // Check that we get the right events at the right time.
        final List<List<ECKey>> listenerKeys = Lists.newArrayList();
        chain.addEventListener(new AbstractKeyChainEventListener() {
            @Override
            public void onKeysAdded(List<ECKey> keys) {
                listenerKeys.add(keys);
            }
        }, Threading.SAME_THREAD);
        assertEquals(0, listenerKeys.size());
        chain.setLookaheadSize(5);
        assertEquals(0, listenerKeys.size());
        ECKey key = chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        assertEquals(1, listenerKeys.size());  // 1 event
        final List<ECKey> firstEvent = listenerKeys.get(0);
        assertEquals(6, firstEvent.size());  // 5 lookahead keys and 1 to satisfy the request.
        assertTrue(firstEvent.contains(key));   // order is not specified.
        listenerKeys.clear();
        chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        key = chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        assertEquals(1, listenerKeys.size());  // 1 event
        assertEquals(5, listenerKeys.get(0).size());  // 5 keys.
        DeterministicKey eventKey = (DeterministicKey) listenerKeys.get(0).get(0);
        assertNotEquals(key, eventKey);  // The key added is not the one that's served.
        assertEquals(6, eventKey.getChildNumber().i());
        listenerKeys.clear();
        key = chain.getKey(HDKeyChain.KeyPurpose.RECEIVE_FUNDS);
        assertEquals(1, listenerKeys.size());  // 1 event
        assertEquals(6, listenerKeys.get(0).size());  // 1 key.
        eventKey = (DeterministicKey) listenerKeys.get(0).get(0);
        // The key added IS the one that's served because we did not previously request any RECEIVE_FUNDS keys.
        assertEquals(key, eventKey);
        assertEquals(0, eventKey.getChildNumber().i());
    }

    @Test
    public void serializeUnencrypted() throws UnreadableWalletException {
        DeterministicKey key1 = chain.getKey(HDKeyChain.KeyPurpose.RECEIVE_FUNDS);
        DeterministicKey key2 = chain.getKey(HDKeyChain.KeyPurpose.RECEIVE_FUNDS);
        DeterministicKey key3 = chain.getKey(HDKeyChain.KeyPurpose.CHANGE);

        List<Protos.Key> keys = chain.serializeToProtobuf();
        // 1 root seed, 1 master key, 1 account key, 2 internal keys, 3 derived, 20 lookahead and 5 lookahead threshold.
        assertEquals(33, keys.size());

        // Get another key that will be lost during round-tripping, to ensure we can derive it again.
        DeterministicKey key4 = chain.getKey(HDKeyChain.KeyPurpose.CHANGE);

        final String EXPECTED_SERIALIZATION = checkSerialization(keys, "deterministic-wallet-serialization.txt");

        // Round trip the data back and forth to check it is preserved.
        int oldLookaheadSize = chain.getLookaheadSize();
        chain = HDKeyChain.fromProtobuf(keys, null).get(0);
        assertEquals(EXPECTED_SERIALIZATION, protoToString(chain.serializeToProtobuf()));
        assertEquals(key1, chain.findKeyFromPubHash(key1.getPubKeyHash()));
        assertEquals(key2, chain.findKeyFromPubHash(key2.getPubKeyHash()));
        assertEquals(key3, chain.findKeyFromPubHash(key3.getPubKeyHash()));
        assertEquals(key4, chain.getKey(HDKeyChain.KeyPurpose.CHANGE));
        key1.sign(Sha256Hash.ZERO_HASH);
        key2.sign(Sha256Hash.ZERO_HASH);
        key3.sign(Sha256Hash.ZERO_HASH);
        key4.sign(Sha256Hash.ZERO_HASH);
        assertEquals(oldLookaheadSize, chain.getLookaheadSize());
    }

    private String protoToString(List<Protos.Key> keys) {
        StringBuilder sb = new StringBuilder();
        for (Protos.Key key : keys) {
            sb.append(key.toString());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String checkSerialization(List<Protos.Key> keys, String filename) {
        try {
            String sb = protoToString(keys);
            String expected = Resources.toString(getClass().getResource(filename), Charsets.UTF_8);
            assertEquals(expected, sb);
            return expected;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
