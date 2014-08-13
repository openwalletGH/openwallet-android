package com.coinomi.core.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.BitcoinTest;
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
import com.google.bitcoin.wallet.KeyChain;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

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
        chain.setLookaheadSize(10);
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
        long secs = 1389353062L;
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
        assertEquals(1, firstEvent.size());
        assertTrue(firstEvent.contains(key));   // order is not specified.
        listenerKeys.clear();

        chain.maybeLookAhead();
        final List<ECKey> secondEvent = listenerKeys.get(0);
        assertEquals(12, secondEvent.size());  // (5 lookahead keys, +1 lookahead threshold) * 2 chains
        listenerKeys.clear();

        chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        // At this point we've entered the threshold zone so more keys won't immediately trigger more generations.
        assertEquals(0, listenerKeys.size());  // 1 event
        final int lookaheadThreshold = chain.getLookaheadThreshold() + chain.getLookaheadSize();
        for (int i = 0; i < lookaheadThreshold; i++)
            chain.getKey(HDKeyChain.KeyPurpose.CHANGE);
        assertEquals(1, listenerKeys.size());  // 1 event
        assertEquals(1, listenerKeys.get(0).size());  // 1 key.
    }

    @Test
    public void serializeUnencryptedNormal() throws UnreadableWalletException {
        serializeUnencrypted(masterKey, DETERMINISTIC_WALLET_SERIALIZATION_TXT_MASTER_KEY);
    }

    @Test
    public void serializeUnencryptedChildRoot() throws UnreadableWalletException {
        DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterKey);
        DeterministicKey rootKey = hierarchy.get(BitcoinTest.get().getBip44Path(0), false, true);
        rootKey.setCreationTimeSeconds(0);

        serializeUnencrypted(rootKey, DETERMINISTIC_WALLET_SERIALIZATION_TXT_CHILD_ROOT_KEY);
    }


    public void serializeUnencrypted(DeterministicKey rootKey, String expectedSerialization) throws UnreadableWalletException {
        chain = new HDKeyChain(rootKey);
        chain.setLookaheadSize(10);

        chain.maybeLookAhead();
        DeterministicKey key1 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        DeterministicKey key2 = chain.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        DeterministicKey key3 = chain.getKey(KeyChain.KeyPurpose.CHANGE);
        List<Protos.Key> keys = chain.toProtobuf();
        // 1 master key, 1 account key, 2 internal keys, 3 derived, 20 lookahead and 5 lookahead threshold.
        int numItems =
                1  // master key
                        + 1  // account key
                        + 2  // ext/int parent keys
                        + (chain.getLookaheadSize() + chain.getLookaheadThreshold()) * 2   // lookahead zone on each chain
                ;
        assertEquals(numItems, keys.size());

        // Get another key that will be lost during round-tripping, to ensure we can derive it again.
        DeterministicKey key4 = chain.getKey(KeyChain.KeyPurpose.CHANGE);

        String sb = protoToString(keys);
        assertEquals(expectedSerialization, sb);

        // Round trip the data back and forth to check it is preserved.
        int oldLookaheadSize = chain.getLookaheadSize();
        chain = HDKeyChain.fromProtobuf(keys, null).get(0);
        assertEquals(expectedSerialization, protoToString(chain.toProtobuf()));
        assertEquals(key1, chain.findKeyFromPubHash(key1.getPubKeyHash()));
        assertEquals(key2, chain.findKeyFromPubHash(key2.getPubKeyHash()));
        assertEquals(key3, chain.findKeyFromPubHash(key3.getPubKeyHash()));
        assertEquals(key4, chain.getKey(KeyChain.KeyPurpose.CHANGE));
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


    // FIXME For some reason it doesn't read the resource file
    private final static String DETERMINISTIC_WALLET_SERIALIZATION_TXT_CHILD_ROOT_KEY =
            "type: DETERMINISTIC_KEY\n" +
                    "secret_bytes: \"\\023\\354\\032\\244\\374<y\\254aq\\a\\362=\\345\\204\\n^;5\\213M\\267\\311\\234\\037RkX\\235\\363ae\"\n" +
                    "public_key: \"\\002T5VO\\274\\362m\\300\\320\\352\\005\\r!\\307\\024\\250\\307C\\324\\323\\215[\\232@\\254S\\270\\217\\362\\370\\214\\362\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"F\\336\\2067\\377M\\026)%\\357ZL#\\203\\320\\324\\217-\\3305\\310\\244\\n\\205\\277E\\323L\\250ww\\314\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "secret_bytes: \"|\\236\\344\\221u\\347\\225\\304$>\\354\\235\\005I\\211\\333O\\343\\345A\\322\\253\\340\\352\\354R\\020\\216J\\232\\372\\312\"\n" +
                    "public_key: \"\\002\\000l@WL\\305Dj~)\\347n\\241\\210\\245\\326{7\\351\\373\\354\\257\\022{ eE\u007F\\004\\355\\267\\362\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"3\\250\\206*.#8$\\237\\n\\222\\255Jv\\302U5m\\027\\301\\302X\\260\\245\\3456|\\2515\\204&\\310\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "secret_bytes: \"\\243\\310\\252\\245B\\032\\212\\246\\227\\270 G\\'Y\\016\\026\\223\\271\\206\\303\\356>A\\364Zkm82\\250\\244\\311\"\n" +
                    "public_key: \"\\003\\262(\\306\\234K\\373\\311q]eR\\237\\330Ch\\221\\321\\335a\\234B\\\\~\\353SA:\\2031W\\372;\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\n\\265\\313\\322\u007F;K\\352e\\300\\306\\226\\314\\363\\241\\347\\203\\364\\357\\005<\\372\\320\\356\\226\\327\\375k\\271*,:\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  issued_subkeys: 2\n" +
                    "  lookahead_size: 10\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "secret_bytes: \"Om\\022\\235\\351\\004\\206\\206\\006\\302\\326\\033\\206\\030\\273i\\035\\34404\\313\\237x\\373D\\247\\374\\266\\032p-\\326\"\n" +
                    "public_key: \"\\002\\377m\\343\\227P\\320\\3254\\247\\347\\367\\311\\250\\300\\311\\2762\\204\\367\\a\\311\\nX\\032\\331>]\\005x\\332A5\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\242\\312$\\360\\323:\\025{\\210\\305\\360\\252\\255;2#)=[t\\232\\2046\\237\\033\\v\\322\\257\\241\\2239D\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  issued_subkeys: 1\n" +
                    "  lookahead_size: 10\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\304\\317\\3124_\\372\\242\\022v\\255\\312\\267\\234\\231\\222\\304\\364J}b\\311,\\240\\345\\033\\321\\025\\336\\333v\\002N\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\347\\3713K#\\224\\356\\004\\335 \\227\\316\\225V15)\\321\\341.\\276\\t?2\\f\\226I\\265z\\315r:\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 0\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002k\\256\\016\\236B\\027<\\362\\212\\t\\305\\314\\270\\237\\267\\224\\214\\250\\223\\325\\237\\215\\273\\345\\347E\\026f\\373\\331\\272\\371\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\316\\037\\202\\242\\261\\273\\270w\\277\\214\\302\\350QeV+\\207\\250\\311\\244\\335\\221\\351\\263k\\260=\\nf\\247\\243\\266\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 1\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\003\\f\\262\\300b\\251\\2278\\037.\\334\\026\\336\\333\\2328Gepm<\\022\\257\\367\\rW\\212LTT\\2559*\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\370\\260\\303\\025\\225[\\032\\024i@-\\n\\025\\t7pD:\\357\\\"\\355\\2325zT\\0057D\\336\\2559f\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 2\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\221r\\2246c\\030\\035u\\203\\215Mp\\356\\225f\\355\\277\\252\\354O\\017\\332E\u007FZ\\224\\3062 Q\\337\\255\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\376\\341tZ\u007Fyb\\264\\202\\\"q\\300\\027o\\3055?#X\\350h\\222\\346\\032\\2114\\3126J\\345\\204\\024\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 3\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\332J\\22109\\020V\\270\\274N\\354\\363\\204Rpa8[\\255\\353U\\230\\3517=<NYF\\225\\373\\357\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\323\\231\\000\\017\\002f\\234k\\246Y\\377\\024Y\\231\\334\\367\\215\\330\\376\\354\\241*\\2132\\360\\210:2+\\221\\354\\000\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 4\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\033x\\202\\365=t\\237\\364\\360\\212O2\\372\\237,Q\\245\\207\\345J\\244\\017\\2015|\\266$\\326\\205\\365\\306\\202\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"!\\357\\261w\\315^\\363\\230\\004\\026\\353\\251\\362WP\\324\\3556L\\260\\375\\376\\n\\204!\\304|8;\\025\\221\\323\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 5\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\020)\\230\\313\\270t\\205\\252T;\\371\\241\\231\\351\\254\\301\\332\\236`\\f<u\\327k\\226\\333hs\\271\\000\\254a\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"2\\315\\241\\203\\277w7\\240n?\\321\\334\\321\\345\\025\\344p\\\\s\\347*&j\\351O]`j\\017fL\\317\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 6\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\364E\\264cY \\253b\\313\\'\\2658\\274\\3177\\253I\\377\\365^\\352\\253QN\\311Fk\\244\\201\\2461\\270\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\231\\375l\\336\\002:\\321+\\305\\243\\277V\\353!\\332PB\\025\\305N\\016\\333<\\301\\3627#\\255\\213\\017\\222\\036\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 7\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\003\\315J\\003+\\333\\215+\\233(\\312U+bCO\\207\\222\\211C\\242I3 \\247\\373\\321\\266\\221,\\271\\030T\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\237GF\\342J\\331\\207@\\272\\346(\\346\\351!!Q\\026\\367\\2562\\357/\\264\\357Xa\\323G\\374\\267\\b\\221\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 8\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\v\\330\\370\\3158\\026\\210\\202\\2714\\372j\\343\\to\\225S\\2724\\017Z\\333\\373Ey\\263\\\"\\373\\242d\\'\\354\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"u\\275FL\\034\\3603\\230?\\2632W\\241\\270\\016\\032Y\\3024\\246&\\303\\206l{I\\322\\337\\202\\026f%\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 9\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\236\\222\\222\\303u\\\"X\\025\\304\\f\\203\\363\\313\\037{{^z\\263\\024\\211 \\250$C\\000\\370\\370}\\346dr\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\333\\373\\332\\313hE\\275\\271\\030\\326\\355\\357`\\021\\255\\352\\203\\237\\307\\313\\'\\020\\247\\251 \\361\\235\\251T8\\032\\374\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 10\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\230:\\2174\\005\\v\\237r\\343%x\\225>\\300\\335\\372\\224\\020\\323\\32565\\206\\300\\265\\'\\244-\\377\\234\\003\\302\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\0344,\\241\\270\\312\\361\\206\\204t\\004@\\202,\\264\\241z\\277\\337j\\366\\310\\342\\344\\331\\'6u\\361\\355\\3555\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 11\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\003nir\\350\\206\\247\\020\\242\\272\\311\\307\\034@\\373\\233\\214-\\321w\\023\\027\\355\\376\\374\\351B\\002\\327T@\\030\\336\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"q&\\211ng*\\245\\332\\312\\226`\\aBR<e\\242\\023\\327\\232\\217\\343v\\275\\255-\\251\\206\\020\\001\\313K\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 0\n" +
                    "  path: 12\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\200\\270\\255f\\273\\337w\\362\\215B5f#\\335\\363l\\241\\374\\f\\027\\374\\v\\212\\263*\\346\\222&\\3749\\337\\304\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"x\\303\\352{\\2010\\232,\\024\\276\\355\\020t&\\277\\275-\\236\\361QK\\303U\\030\\031\\347}I}\\211R\\\\\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 0\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\003!\\204z\\n\\242\\317\\216C6\\237\\212EtupH\\217\\365\\256\\230\\363\\236\\335\\272u@\\'\\330!\\024J\\211\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\212\\372\\322\\301\\201\\032{=H\\210\\314\\240\\r\\a\\336\\315S=\\354@$(X!\\314\\206\\326\\214\\336&\\350Z\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 1\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\003\\\"\\335[|\\262\\244\\214,)\\001\\241\\034\\375\\301\\350\\003j:\\002A\\364\\321\\263\\244g\\272\\271\\336\\331\\335M\\026\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"m\\245\\244\\304\\341:\\364\\307\\247V;\\264\\314S\\304\\242\\263\\302$\\361\\314\\317NY\\206Dd%p\\360Z\\201\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 2\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\225q\\346\\256\\206M\\354\\024\\324\\242\\351\\315\\355\\377\\'\\234\\251B\\333@\\223l\\336E\\336}\\301\\0314\\223/\\003\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"T\\2351\\026\\020\\256\\305\\275\\204]\\312\\320\\246W\\370\\326\\2304\\006\\323\\a\\n\\006\\215R\\371V\\003\\267DV[\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 3\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\213t\\311\\215k\\376}\\243\\217j\\3726\\255\\001I\\275\\004\\305\\r\\201\\317\\273\\365?y\\b\\236p\\275/;\\334\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\314\\216$\\255\\336\\273\\240*\\366\\375\\234\\334_5\\226`\\266\\350\\370(\\0168\\344\\235\\307\\002\\341\\033K\\250$\\267\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 4\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\026@\\272\\333\\252\\326\\224\\261\\320\\326\\020\\347\\031t\\0321\\275;\\260\\032\\b\\000\\006 1#80\\300\\236\\317R\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"8\\222\\242\\025N\\320\\0169\\205\\267\\300\\213<\\205\\352\\377]\\333v\\006\\202{?Q\\032\\344\\3207\\343\\267\\320\\221\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 5\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\003\\243\\367\\356\\2064\\204\\373fH\\224\\327e\\370\\267?o\\247\\315luaYC\\n\\221\\312\\337\\273\\252)e\\374\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"^u\\250\\320\\373\\2316\\332\\026\\334\\323\\301\\376\\214\\373m]\\376-N\\322\\200T\\255\\231\\347\\347J\\345\\020\\262M\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 6\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\003F\\000\\246\\210J\\376\\0220\\a\\005\\335\\344\\315\\375BN\\220\\306\\001Wn\\247 T\\206A8\\312u\\263i\\\\\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\273\\274^@\\001\\375\\202R\\250\\336\\356\\375\u007F\\273\\372\\375\\353?[x\\375\\310\\223<\\272U4N\\rO]-\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 7\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\003\\334I$\\372x\\ts\\f\\030\\364\\001\\344\\361\\211\\257\\212\\221\\\\\\253\\027\\222\\017J\\255Y\\233\\000|*\\207i\\306\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\334\\311\\217\\260a\\254\\232\\270\\034\\301mg2\\030\\365\\314Nc\\023$D\\233+\\vt\\252\\352\\312\\301\\260\\311,\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 8\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\034\\212\\031\\002\\351\\367\\032Mb\\374\\2271\\246=\\306\\201\\376\\323>\\212~\\n81/\\226\\366j\\257\\2579\\306\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"O`\\002\\35619\\320\\022\\b\\017\\367\\342\\227_5\\245\\017\\257x\\301\\344/B\\210\\026\\025\\211\\307\\361^\\320\\r\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 9\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\333\\303fa\\016\\311.\\210F\\200\\376j\\017\\023N|Q4%P\\027\\346\\331&\\257\\234kA\\222\\317\\\"\\224\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"^8\\377\\312\\331\\245A8\\334\\315\\341?\\313\\331y\\030b\\331P\\0311\\271[R\\335\\317n\\302\\b\\f\\334\\034\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 10\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\374\\245\\271R\\314\\230D\\332\\216r\\004\\004\\217y\\231\\252f\\000\\021O\\330\\020\\200\\200\\205^-\\326iV\\301\\243\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"\\330\\275bNw\\205\\300\\210#\\001\\265\\341\\254\\360\\'\\3018\\023\u007F\\206\\227\\330\\355\u007FO\\t3\\320\\247\\030\\233\\376\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 11\n" +
                    "}\n" +
                    "\n" +
                    "type: DETERMINISTIC_KEY\n" +
                    "public_key: \"\\002\\2573\\356O\\217\\232\\2724\\337;\\233\\232\\225\\222P\\375j\\272\\001\\305+\\266)\\312\\217?{njb\\324c\"\n" +
                    "deterministic_key {\n" +
                    "  chain_code: \"J\\263\\327\\257\\036\\344\\325$\\355~D\\004<\\341\\'\\345:\\360\\325\\2067\\005E\\'N\\211x\\031\\302\\031\\332\\301\"\n" +
                    "  path: 2147483692\n" +
                    "  path: 2147483649\n" +
                    "  path: 2147483648\n" +
                    "  path: 2147483648\n" +
                    "  path: 1\n" +
                    "  path: 12\n" +
                    "}";

    private final static String DETERMINISTIC_WALLET_SERIALIZATION_TXT_MASTER_KEY =
            "type: DETERMINISTIC_KEY\n" +
            "secret_bytes: \"\\270E0\\202(\\362b\\023\\276\\264\\347\\226E2\\360\\221\\347\\325\\233L\\203\\3276\\272\\213\\2436&\\304\\373\\221\\025\"\n" +
            "public_key: \"\\002\\342$\\253\\332\\031\\352\\324q\\316M\\251}\\274\\267\\370X$\\366>Q\\316\\005\\330\\376\\353f!WHLL\\a\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"XL\\240FW\\203\\316\\230\\334\\374J\\003\\357=\\215\\001\\206\\365\\207Z\\006m\\334X`\\236,;_\\304\\000^\"\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "secret_bytes: \"\\354B\\331\\275;\\000\\254?\\3428\\006\\220G\\365\\243\\333s\\260s\\213R\\313\\307\\377f\\331B\\351\\327=\\001\\333\"\n" +
            "public_key: \"\\002\\357\\\\\\252\\376]\\023\\315\\'\\316`\\317\\362\\032@\\232\\\"\\360\\331\\335\\221] `\\016,\\351<\\b\\300\\225\\032m\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\370\\017\\223\\021O?.@gZ|\\233j\\3437\\317q-\\241!\u007FJ \\323\\'\\264s\\203\\314\\321\\v\\346\"\n" +
            "  path: 2147483648\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "secret_bytes: \"<M\\020I\\364\\276\\336Z\\255\\341\\330\\257\\337 \\366E_\\027\\2433w\\325\\263\\\"$\\350\\f\\244\\006\\251u\\021\"\n" +
            "public_key: \"\\002\\361V\\216\\001\\371p\\270\\212\\272\\236%\\216\\356o\\025g\\r\\035>a\\305j\\001P\\217Q\\242\\261.\\353\\367\\315\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\231B\\211S[\\216\\237\\277q{a\\365\\216\\325\\250\\223s\\v\\n(\\364\\257@3c\\312rix\\260c\\217\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  issued_subkeys: 2\n" +
            "  lookahead_size: 10\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "secret_bytes: \"\\f0\\266\\235\\272\\205\\212:\\265\\372\\214P\\226\\344\\a{S0\\354\\250\\210\\316L\\256;W\\036\\200t\\347\\343\\246\"\n" +
            "public_key: \"\\0022\\n\\252\\267NDr.7i7\\332\\232x\\367\\204G-|\\204\\301\\333G\\033g\\300O\\241\\006\\217\\366\\370\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\213\\237\\327\\245a\\273\\274\\310\\377\\360\\351\\352<\\211k\\033g\\0251>y\\236\\345Jb\\244[\\b\\fO\\0311\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  issued_subkeys: 1\n" +
            "  lookahead_size: 10\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002O_Q\\223\\337\\360\\245\\234\\322b_op\\b?\\030\\364\\255l\\206\\344`w\\274\\204\\\"\\257\\235U<}\\377\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\331\\233\\342\\227\\336r\\212>\\021\\022p\\347* +\\220\\021{\\206\\310Z\\314\\335\\322\\230\\331\\365\\221}\\321\\036\\035\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 0\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\\270\\311\\006\\363\\375\\002{\\310\\254n\\301\\366\\303\\315\\255\\3462\\004/\\251\\'\\205+\\341~d\\275\\350\\\"\\313\\204\\313\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"5\\037!\\360\\335\\017\\276\\231\\273\\3531\\020\\253\\223 \\312\\240M+\\250\\2520e\\006\\034\\214{\\331\\376\\201\\004\\306\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 1\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\\000\\n\\256n\\324$.\\324\\365\\231\\f\\224\\001\\376\\266\\341\\036Q\\212\\374>\\245\\324\\\\8*\\342\\370\\251x\\b-\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"5\\202n|A\\251$y+t\\005\\365\\231\\357\\323\\264E\\266l\\220\\367\\211dA\\306\\370\\247<\\'\\034\\323\\324\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 2\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002\\313/\\026\\020\\254\\240\\3455\\216\\342E\\300\\316\\353m.\\270\\204\\264\\327\\220H\\326E9\\310\\227 \\023~\\204\\215\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\342\\263a\\033~\\374\\234UN\\034\\302\\300\\370\\232\\347B#L\\251\\267\\035\\255\\210\\356\\vE\\264\\210\\317\\030]t\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 3\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002\\217\\n\\021GL\\354\\214\\354WhX\\254\\351\\337w.\\211&q1o\\003\\033\\330\\352**\\351\\356\\210\\264m\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\036\\216\\345\\320e\\267p\\241\\000\\204\\254\\370\\251d\\000\\253\\354\\316RH\\275RS\\221\\016\\343=T\\236\\335\\222P\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 4\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\\325\\n\\347\\346\\3273\\312J\\211e\\335?\\227\\236\\304i\\227\\377J\\222;\\253\\017\\213\\371\\235d\\220\\231\\026aV\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"YSn>5\\364i(j\\b\\326\\212,\\f,\\322\\3200\\230\\210)\\366g\\201\\274\\232\\356\\027\\212O\\345\\215\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 5\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\\264\\331\\220\\207*\\342T\\277\\323\\363\\210\\266\\335\\300\\245?\\024d\\002\\021\\263|\\253\\035\\253\\244D\\023\\004\\200\\212X\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"yP\\342|\\327\\364\\034\\f\\302}\\236\\032\\031\\t\\345h(q7\\346?wR\\221\\325\\370\\021\\225\\334\\317Bg\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 6\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002HX\\261\\035\\270!\\263\\2232-F\\334\\226n=<\\0178\\270^\\202\\225\\264PF\\v#\\bdP/\\355\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"Z#\\227\\222\\225\\303\\203\\006q\\206\\321\\v\\355\\353\\220#Oh\\360]\\001IQD\\333\\025\\356\\276\\342\\270\\021\\313\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 7\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002\\020C\\2310\\227\\302\\342\\274u\\217\\021h\\270\\235\\356\\326_\\365\\321\\261\\272\\340\\267\\n\\335~\\360\\343\\\"Ow\\b\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\232\\000\\3117\\235\\003`)\\021g}/\\203tk\\201\\021\\364\\247\\245;\\253\\321\\202\\207\\342\\265\\267_<\\206\\224\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 8\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002\\276\\211n\\305\\3339[D\\337+\\034\\263\\267U0\\263\\3039}/\\376\\207\\030\\364K\\335\\301\\245\\311\\241\\3419\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"B\\232\\f\\')\\277\\034\\316HOdn\\213\\b\\204\\361\\030\\357YS \\365zY\\2749e\\260)\\233.-\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 9\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002h\\356\\202\\222P\\204x\\345\\226\\017\\256/E\\304\\273{)\\272\\034\\375\\2451\\321\\257\\233\\372?\\320\\352\\356\\271K\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\035\\222\\325\\fY\\221(\\006\\tf#7\\370\\355el\\377\\276\\245\\354\\002\\327\\320\\247\\315V\\004\\016v\\367\\351\\225\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 10\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002\\325\\313@\\\"\\320\\035\\277(\\245\\222\\342{\\374g\\235\\203\\347\\217\\035\\204j\\027\\034\\244\\021bY0\\247P`\\323\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\226~!\\327\\210.\\033\\214\\251\\2367\\205<\\226`UF\\354\\234/\\365\\267E\\317\\202\\354\\211P\\244\\221\\336\\200\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 11\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\\000\\334\\035\\2400n\\26636x\\316\\327\\3666\\271\\375K\\031\\366\\307\\221J@\\331@dL\\232Bv\\324\\262\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\207^n\\317\\370\\t\\207\\341*\\\\\\360\\026iBRTQ#\\252Z\\237\\373{\\315\\333\\004\\340nA9\\252\\352\"\n" +
            "  path: 2147483648\n" +
            "  path: 0\n" +
            "  path: 12\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002\\225b\\3515\\202\\233\\335\\320.7\\265\\274uh\\230N\\242\\254\\317\u007FJ\\364\\331\\2345\\220)\\362\\334\\216\\202\\\\\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\202:\\344\\3109?\\350\\345\\001\\314(\\244q\\370\\233Rk\\261}\\302(\\275\\326\\305R\\342:\\246\\036\\nV\\330\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 0\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003>K!8\\222VEL\\371\\305 z\\aD\u007F8\\020\\233\\330S\\251T\\330\\201V\\026-k2\\227\\266;\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\223\\265.\\200\\316\\361\\241{\\223\\342c\\212\\0213ym+\\032=#\\360\\333X\\003\\2770Z\\311\\335\\267\\342\\313\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 1\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\\331t\\251d\\023\\355w\\221\\266\\301\\264\\306T\\252\\350\\200\\260A\\220\\363\\212\\345\\021\\222\\236\\003\\210\\215\\342\\r\\251\\000\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\276\\262\\033\\030\\227\\271&e\\254\\377\\346\\031\\2112\\344[\\234Z\\221-\\033\\306P,Mi\\021\\313r\\031\\317\\341\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 2\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002D\\374\\231\\027\\306\\310\\251\\261\\200\\350@\\ro\\314\\216\\037>rp\\017\\276Q\\203\\027\\016\\213\\320\\206VqO\\237\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"_K4\\n\\356\\235\\036\\243O\\261\\200\\004\\367\\324\\305;1\\247I\\350*\\353`\\204\\004d\\202\\302\\335\\200/#\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 3\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\\370\\352\\3530]|\\262\\270]5\\361\\263\\255)\\027f\\342\\262\\272a-\\275\\006\\302\\266\\236\\344\\332\\364\\r\\260\\321\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"o!GH\\357\\030\\264\\003_S\\305\\204\\234wO\\344.\\215\\377\\232\\025\\206\\351\\030\\227,\\303%U2x\\225\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 4\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002\\221\\021\\370a[\\205\\267\\036\\021\\366`\\036\\371\\253Yk\\r\\303\\025\\f\\255\\2768\\310\\212\\234\\221\u007F\\333\\344\\340t\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\370~\\245F\\n\\307\\377Q:\\v\\207\\245\\336F\\376\\2443R\\034\\346\\b\u007F\\372\\b\\\\o\\303\\204D#}\\266\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 5\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002c\\034w@c\\225\\257n~G\\330\\002\\241^\\264\\231\\030\\025\\220gr\\202`u\\b\\262\\361\\312\\246\\202J\\341\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\\\\\2542\\003\\022\\254\\361*\\a/4\u007F\\307\\3430\\322\\303\\v\\205\\351\\027\\260 l\\332\\326\\235<\\363v\\020\\232\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 6\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\\266\\304\\006g\\244l\\271>\\364\\357G8B\\374\\026w\\316\\022\\205\\313\\220\\274\\273>$\\350\\212o!\\rt\\230\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"6]\\325WN\\017o\\255\\314\\213\\344\\201f\\204\\361\\235\\'\\343\\217\\341m\\327\\326=T\\2018g\\324\\261`\\335\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 7\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003X\\331\\344\\227G\\366//<V\\226\\b\\352#\\315\\307j\\263\\232\\273d\\236)\\004\\225fk\\304\\000XM\\305\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"&\\025?\\264\\a\\2334-\\203\\217\\240R\\221[{8)9\\221\\346bv=ut\\346\\226KVj\\2659\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 8\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003\u007F\\307\\273B\\334\\212\\303\\025r\\212\\264|\\250c\\204\\\\=\\360w\\335\\300\\353\\266\\273\\3209\\260nl3\\271+\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\345\\365\\034\\261\\316\\2121R\\226/+\\267K\\326C&\\236\\246],\\224\\001\\220\\347\\334\\351\\223K\\023\\252\\360\\023\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 9\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002+[\\230[E\\225\\225R2\\350X=\\366\\343\\244\\237\\260\\220J\\311\\376\\200@\\\\\\334\\312y\\212\\276\\223\\350\\267\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\250W]}O:@\\t\\016\\311\\016\\335\\016\\271\\260\\327\\261\\237\\030G\\334\\246\\233\\352t\\266\\\\S\\311\\333m*\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 10\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\003-\\221uJ\\237\\240\\320\\025\\031w\\001V\\276\\030j\\217Z\\222 \\330\\253\\332\\330F\\216\\377D\\311\\211\\277\\351\\230\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"\\241\\363\\245\\033W\\f*J\\026\\021\\210Ic\\2318a\\\"\\036\\302\\005+\\220\\003\\3364\\211o\\362\\225R~\\340\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 11\n" +
            "}\n" +
            "\n" +
            "type: DETERMINISTIC_KEY\n" +
            "public_key: \"\\002V\\3212\\255\\n\\367\\226%]0\\342\\003\\317\\031\\350\\265K\\247\\035\\005}\\004[N\\262\\262\\376Ed\\261j\\377\"\n" +
            "deterministic_key {\n" +
            "  chain_code: \"0\\236\\330H\\354\\237\\016\\367-/E\\344\\311\\024\\353\\307\\331\\367n\\017\\250n\\351\\000\\204\\233\\224\\242L\\343&;\"\n" +
            "  path: 2147483648\n" +
            "  path: 1\n" +
            "  path: 12\n" +
            "}";
}
