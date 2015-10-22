package com.coinomi.core.wallet.families.vpncoin;

import com.coinomi.core.coins.VpncoinMain;
import com.coinomi.core.messages.MessageFactory;
import com.google.common.base.Charsets;

import org.bitcoinj.core.Transaction;
import org.junit.Test;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.util.regex.Matcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author John L. Jegutanis
 */
public class TransactionTest {
    static byte[] ORIGINAL_BYTES = "hello".getBytes(Charsets.UTF_8);
    static byte[] ENCRYPTED = Base64.decode("AwLL/0IqTyNPIA==");
    static long ENCRYPTION_KEY = 123;
    static byte[] COMPRESSED = Base64.decode("AAAABXicy0jNyckHAAYsAhU=");

    static String FROM_USER = "user@domain.com";
    static String SUBJECT = "A 'lorem ipsum' message for you";
    static String MESSAGE = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.\n" +
            "Donec a diam lectus. Sed sit amet ipsum mauris.\n" +
            "Maecenas congue ligula ac quam viverra nec consectetur ante hendrerit.\n" +
            "Donec et mollis dolor. Praesent et diam eget libero egestas mattis sit amet vitae augue.";

    String TX_HASH_1 = "192c485deab867c994a2396c815a48e4c3808c166b4edd70f87085e22c1417e5";
    Transaction TX_1 = new Transaction(VpncoinMain.get(), Hex.decode("0100000089cec45502c8f83c02c59583876c6e1fa447e7ab6083e392753ddf8707269b36340afcf028000000006a47304402206552a703cc1865c72bc43899d4841502f52f5110d2cb637983965e153abe197802200b31b94c1d014fc0ea360f5d29a79ab98421f6104145477a7077540393aeb0bd0121028cbb0a3d32a4e076c5131c90b49ddcb940eaa67cf918a1484ca69d19d475a579ffffffff35908ed4a3aa92f2c0b19605a07fbb9a76146db542cc30b9d5e75d2259ac8735010000006a473044022053a2f59dc313e43c96b01b3787bfab948aa1b95a40a1b9691eff01545d924d9e02204845df419347bff68207febe7fe05bed41daa5164d7f26ec170cac7455b0e4d601210282a4a113c70c352ee86d2a60297a7eeb719664f8610514d442346d7999ffa3b6ffffffff0220b81800000000001976a914a620a48d22391100afceaff72c73a4032f3fb02b88ac00e40b54020000001976a914ce7f924d1886c3525e8454eebda251a0f0d1b10b88ac00000000384177494d6465373776652b6737566e5665466c354e5641312f47487735363251324c31592b6c464543566f64495031622f73577533664d3d"));
    String TX_HASH_2 = "9925c0724b5ce11e13c9cb1349cb084377ab6e3245112eec109d54d3bcf38b34";
    Transaction TX_2 = new Transaction(VpncoinMain.get(), Hex.decode("01000000347bc75501f4fddc83d4a27cb80e73547503e23f3f9336effa180aed06cf60c65f6625059c010000006b483045022100be8e81194d4e77ca53cf472973f065f04709a8fe7b429955b829b08c69ac6213022040db08bb6f8a9d8a78157752c1f534239d9d339546423d5cfd1462fda5408b9101210351548af711794d4e556d2c215bd25f9d2a4d7ec5c99d5ca42d4c33fb21307835ffffffff02f09eb31e030000001976a914a620a48d22391100afceaff72c73a4032f3fb02b88ac00e40b54020000001976a914ce7f924d1886c3525e8454eebda251a0f0d1b10b88ac000000006c4177493672364f32384b4c746f4b6b30584a313638307173426642336353526d4c424842425750515566362b3835536f55745a58687a6e705938692b4f724e6a316761504a46766561324776452b6b384e6430536c316e6c483955596c535438543563457075566f77513d3d"));
    String TX_HASH_3 = "51f0ce3a8c56078d2d05660a042b820dffd2c81f370ab5af0d1135477461348a";
    Transaction TX_3 = new Transaction(VpncoinMain.get(), Hex.decode("01000000f07ac75501b14681fad878ae6d2416816be38bc926a5d428c7140a43e8dc4a64b61f324306010000006b483045022100f7beded6ce2b642af5523e1141032c68bc76fed629080766891b7aa54a4831b802200ad9a30203b93a3661f8132e5f132d865328874e709fe14b953c2ab72e20c7d50121025e3c4bfdc18ea84ec3403360c4b5f01b71fc4cdca5e170e508b05a121ffd5230ffffffff02f00f1789000000001976a914a620a48d22391100afceaff72c73a4032f3fb02b88ac00e40b54020000001976a914ce7f924d1886c3525e8454eebda251a0f0d1b10b88ac00000000fd900241774d4b5161667938764532546d53443139395669636e46735751425631766c70495256726e6e6b597149774d36734c6c6b7a736b793946414c58744b75317a716859417578796c3831717542773245694462426a7245716b3868532f4e5467563476334e3872752f304151656a384f7578392b75676d6e6a575231705945715a5a484d34323338794f50305076613668355935437a54654959715047513279374c54572b4e456c6e354d796e7155586e465739774944687776582b4d3347464771534266746651743654666152776373743631436e4876397a704d6263546170644e6b7249767a474533684a4b2b6945577147434a6f644c4274426b656b7035365073787558716562653068764843724b4f4e7553587a5a6447436f506868344b476f635876394b6c4b6d53706f6863434e4f504f2f376d47784874503159577747636336687644764e4d653374454b5179424835526c4d3771637067513764563174647374646c4f704c50696f643865666b57704336584e4135633259546266325a66376d2b524c2b3367586763636b32616f764349305778336575437a5642486963664c346634376247413634314e2b59523561724376645458444e667665586e446a723065443263474f594639672f433646625a7165553567376947466a79715a6467504134326b4b5176496b54437a796c464576334a6a2b5859526f537635367a7979595a65544563484b6d2f446f454f6162736d5633467542786b5337377845352b455a447a74735a42754843667234304e68616c5967532b2b41644f684273533069575150504646496349326b42663963506b6d356842763161717654346a663145426a2b73483076793444764a7a747233392f33715733513064374b797665704c3338646b413d3d"));

    @Test
    public void decrypt() throws Exception {
        assertArrayEquals(ORIGINAL_BYTES, VpncoinTxMessage.decryptBytes(ENCRYPTION_KEY, ENCRYPTED));
    }

    @Test
    public void uncompress() throws Exception {
        assertArrayEquals(ORIGINAL_BYTES, VpncoinTxMessage.uncompress(ByteBuffer.wrap(COMPRESSED)));
    }

    @Test
    public void hashes() {
        assertEquals(TX_HASH_1, TX_1.getHashAsString());
        assertEquals(TX_HASH_2, TX_2.getHashAsString());
        assertEquals(TX_HASH_3, TX_3.getHashAsString());
    }
    @Test
    public void messageRegex() {
        String messageString = "@FROM=" + FROM_USER + "@SUBJ=" + SUBJECT + "@MSG=" + MESSAGE;
        Matcher matcher = VpncoinTxMessage.MESSAGE_REGEX.matcher(messageString);
        assertTrue(matcher.find());
        assertEquals("@FROM=" + FROM_USER, matcher.group());
        assertTrue(matcher.find());
        assertEquals("@SUBJ=" + SUBJECT, matcher.group());
        assertTrue(matcher.find());
        assertEquals("@MSG=" + MESSAGE, matcher.group());
    }

    @Test
    public void emptyMessages() {
        MessageFactory factory = VpncoinTxMessage.getFactory();
        assertEquals("", factory.createPublicMessage("").toString());
        assertEquals("", factory.createPublicMessage("     ").toString());
        assertEquals("", factory.createPublicMessage("\n\n").toString());
        assertEquals("", factory.createPublicMessage("   \t \t").toString());

        assertEquals(0, ((VpncoinTxMessage)factory.createPublicMessage("")).serialize().length);
        assertEquals(0, ((VpncoinTxMessage)factory.createPublicMessage("     ")).serialize().length);
        assertEquals(0, ((VpncoinTxMessage)factory.createPublicMessage("\n\n")).serialize().length);
        assertEquals(0, ((VpncoinTxMessage)factory.createPublicMessage("   \t \t")).serialize().length);
    }

    @Test
    public void messageStringParsing() {
        VpncoinTxMessage message = VpncoinTxMessage.parse("@FROM=" + FROM_USER + "@SUBJ=" + SUBJECT + "@MSG=" + MESSAGE);
        assertEquals(FROM_USER, message.getFrom());
        assertEquals(SUBJECT, message.getSubject());
        assertEquals(MESSAGE, message.getMessage());
        assertEquals(FROM_USER + "\n\n"+SUBJECT+"\n\n"+MESSAGE, message.toString());

        message = VpncoinTxMessage.parse("@MSG=" + MESSAGE);
        assertNull(message.getFrom());
        assertNull(message.getSubject());
        assertEquals(MESSAGE, message.getMessage());
        assertEquals(MESSAGE, message.toString());
    }

    @Test
    public void messageSerialization() {
        VpncoinTxMessage message = new VpncoinTxMessage(FROM_USER, SUBJECT, MESSAGE);
        String expected = "@FROM="+FROM_USER+"@SUBJ="+SUBJECT+"@MSG="+MESSAGE;
        assertArrayEquals(expected.getBytes(Charsets.UTF_8), message.serialize());

        message = new VpncoinTxMessage(MESSAGE);
        assertNull(message.getFrom());
        assertNull(message.getSubject());
        assertArrayEquals(("@MSG=" + MESSAGE).getBytes(Charsets.UTF_8), message.serialize());
    }

    @Test
    public void messageToString() {
        VpncoinTxMessage message = new VpncoinTxMessage(FROM_USER, SUBJECT, MESSAGE);
        assertEquals(FROM_USER + "\n\n"+SUBJECT+"\n\n"+MESSAGE, message.toString());

        message = new VpncoinTxMessage(MESSAGE);
        assertEquals(MESSAGE, message.toString());
    }
/*
    @Test
    public void messagesEncrypted() {
        VpncoinTxMessage message = VpncoinTxMessage.parse(TX_1);
        assert message != null;
        assertEquals("Bit Lee", message.getFrom());
        assertEquals("Hello", message.getSubject());
        assertEquals("Thanks.", message.getMessage());

        message = VpncoinTxMessage.parse(TX_2);
        assert message != null;
        assertEquals("比特李", message.getFrom());
        assertEquals("信息", message.getSubject());
        assertEquals("сообщение\n" +
                "μήνυμα\n" +
                "رسالة", message.getMessage());

        message = VpncoinTxMessage.parse(TX_3);
        assert message != null;
        assertNull(message.getFrom());
        assertNull(message.getSubject());
        assertEquals("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec a diam lectus. " +
                "Sed sit amet ipsum mauris. Maecenas congue ligula ac quam viverra nec consectetur " +
                "ante hendrerit. Donec et mollis dolor. Praesent et diam eget libero egestas mattis " +
                "sit amet vitae augue. Nam tincidunt congue enim, ut porta lorem lacinia consectetur. " +
                "Donec ut libero sed arcu vehicula ultricies a non tortor. Lorem ipsum dolor sit amet, " +
                "consectetur adipiscing elit. Aenean ut gravida lorem. Ut turpis felis, pulvinar a " +
                "semper sed, adipiscing id dolor. Pellentesque auctor nisi id magna consequat sagittis. " +
                "Curabitur dapibus enim sit amet elit pharetra tincidunt feugiat nisl imperdiet. Ut " +
                "convallis libero in urna ultrices accumsan. Donec sed odio eros. Donec viverra mi " +
                "quis quam pulvinar at malesuada arcu rhoncus. Cum sociis natoque penatibus et magnis " +
                "dis parturient montes, nascetur ridiculus mus. In rutrum accumsan ultricies. Mauris " +
                "vitae nisi at sem facilisis semper ac in est.", message.getMessage());
    }
    */
}
