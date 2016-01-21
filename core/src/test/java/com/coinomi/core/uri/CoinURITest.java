package com.coinomi.core.uri;

/*
 * Copyright 2012, 2014 the original author or authors.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.BitcoinTest;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DashMain;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.NuSharesMain;
import com.coinomi.core.coins.NxtMain;
import com.coinomi.core.coins.PeercoinMain;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.families.bitcoin.BitAddress;
import com.coinomi.core.wallet.families.nxt.NxtAddress;

import org.bitcoinj.core.Coin;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;

import static com.coinomi.core.util.BitAddressUtils.getHash160;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CoinURITest {
    private CoinURI testObject = null;
    final CoinType BTC = BitcoinMain.get();
    final CoinType BTC_TEST = BitcoinTest.get();
    final CoinType LTC = LitecoinMain.get();
    final CoinType DOGE = DogecoinMain.get();
    final CoinType PPC = PeercoinMain.get();
    final CoinType DASH = DashMain.get();
    final CoinType NBT = NuBitsMain.get();
    final CoinType NSR = NuSharesMain.get();
    final CoinType NXT = NxtMain.get();


    private static final String MAINNET_GOOD_ADDRESS = "1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH";

    @Test
    public void testConvertToCoinURI() throws Exception {
        BitAddress goodAddress = BitAddress.from(BitcoinMain.get(), MAINNET_GOOD_ADDRESS);
        
        // simple example
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello&message=AMessage", CoinURI.convertToCoinURI(goodAddress, BTC.value("12.34"), "Hello", "AMessage"));
        
        // example with spaces, ampersand and plus
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello%20World&message=Mess%20%26%20age%20%2B%20hope", CoinURI.convertToCoinURI(goodAddress, BTC.value("12.34"), "Hello World", "Mess & age + hope"));

        // no amount, label present, message present
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?label=Hello&message=glory", CoinURI.convertToCoinURI(goodAddress, null, "Hello", "glory"));
        
        // amount present, no label, message present
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=0.1&message=glory", CoinURI.convertToCoinURI(goodAddress, BTC.value("0.1"), null, "glory"));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=0.1&message=glory", CoinURI.convertToCoinURI(goodAddress, BTC.value("0.1"), "", "glory"));

        // amount present, label present, no message
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello", CoinURI.convertToCoinURI(goodAddress, BTC.value("12.34"), "Hello", null));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=12.34&label=Hello", CoinURI.convertToCoinURI(goodAddress, BTC.value("12.34"), "Hello", ""));
              
        // amount present, no label, no message
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=1000", CoinURI.convertToCoinURI(goodAddress, BTC.value("1000"), null, null));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?amount=1000", CoinURI.convertToCoinURI(goodAddress, BTC.value("1000"), "", ""));
        
        // no amount, label present, no message
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?label=Hello", CoinURI.convertToCoinURI(goodAddress, null, "Hello", null));
        
        // no amount, no label, message present
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?message=Agatha", CoinURI.convertToCoinURI(goodAddress, null, null, "Agatha"));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS + "?message=Agatha", CoinURI.convertToCoinURI(goodAddress, null, "", "Agatha"));
      
        // no amount, no label, no message
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS, CoinURI.convertToCoinURI(goodAddress, null, null, null));
        assertEquals("bitcoin:" + MAINNET_GOOD_ADDRESS, CoinURI.convertToCoinURI(goodAddress, null, "", ""));
    }

    @Test
    public void testAltChainsConvertToCoinURI() throws Exception {
        byte[] hash160 = BitAddress.from(BitcoinMain.get(), MAINNET_GOOD_ADDRESS).getHash160();
        String goodAddressStr;
        BitAddress goodAddress;

        // Litecoin
        goodAddress = BitAddress.from(LTC, hash160);
        goodAddressStr = goodAddress.toString();
        assertEquals("litecoin:" + goodAddressStr + "?amount=12.34&label=Hello&message=AMessage", CoinURI.convertToCoinURI(goodAddress, LTC.value("12.34"), "Hello", "AMessage"));

        // Dogecoin
        goodAddress = BitAddress.from(DOGE, hash160);
        goodAddressStr = goodAddress.toString();
        assertEquals("dogecoin:" + goodAddressStr + "?amount=12.34&label=Hello&message=AMessage", CoinURI.convertToCoinURI(goodAddress, DOGE.value("12.34"), "Hello", "AMessage"));

        // Peercoin
        goodAddress = BitAddress.from(PPC, hash160);
        goodAddressStr = goodAddress.toString();
        assertEquals("peercoin:" + goodAddressStr + "?amount=12.34&label=Hello&message=AMessage", CoinURI.convertToCoinURI(goodAddress, PPC.value("12.34"), "Hello", "AMessage"));

        // Darkcoin
        goodAddress = BitAddress.from(DASH, hash160);
        goodAddressStr = goodAddress.toString();
        assertEquals("dash:" + goodAddressStr + "?amount=12.34&label=Hello&message=AMessage", CoinURI.convertToCoinURI(goodAddress, DASH.value("12.34"), "Hello", "AMessage"));

        // NXT
        String pubkeyStr = "3c1c0b3f8f87d6efdc2694ce43f848375a4f761624d255e5fc1194a4ebc76755";
        byte[] pubkey = Hex.decode(pubkeyStr);
        NxtAddress nxtGoodAddress = new NxtAddress(NXT, pubkey);
        goodAddressStr = nxtGoodAddress.toString();
        assertEquals("nxt:" + goodAddressStr + "?amount=12.34&label=Hello&message=AMessage&pubkey="+pubkeyStr,
                CoinURI.convertToCoinURI(nxtGoodAddress, NXT.value("12.34"), "Hello", "AMessage", pubkeyStr));
    }

    @Test
    public void testSharedCoinURI() throws Exception {
        byte[] hash160 = BitAddress.from(BitcoinMain.get(), MAINNET_GOOD_ADDRESS).getHash160();

        // Bitcoin and Bitcoin Testnet
        BitAddress address = BitAddress.from(BTC, hash160);
        testObject = new CoinURI(BTC.getUriScheme() + ":" + address);
        assertTrue(testObject.hasType());
        assertEquals(BTC, testObject.getType());
        assertEquals(address, testObject.getAddress());

        BitAddress addressTestnet = BitAddress.from(BTC_TEST, hash160);
        testObject = new CoinURI(BTC_TEST.getUriScheme() + ":" + addressTestnet);
        assertEquals(BTC_TEST, testObject.getType());
        assertEquals(addressTestnet, testObject.getAddress());

        // NuBits and NuShares
        BitAddress nuBitAddress = BitAddress.from(NBT, hash160);
        testObject = new CoinURI(NBT.getUriScheme() + ":" + nuBitAddress);
        assertEquals(NBT, testObject.getType());
        assertEquals(nuBitAddress, testObject.getAddress());

        BitAddress nuSharesAddress = BitAddress.from(NSR, hash160);
        testObject = new CoinURI(NSR.getUriScheme() + ":" + nuSharesAddress);
        assertEquals(NSR, testObject.getType());
        assertEquals(nuSharesAddress, testObject.getAddress());
    }

    @Test
    public void testAltChainsGoodAmount() throws Exception {
        byte[] hash160 = BitAddress.from(BitcoinMain.get(), MAINNET_GOOD_ADDRESS).getHash160();
        String goodAddressStr;
        BitAddress goodAddress;

        // Litecoin
        goodAddress = BitAddress.from(LTC, hash160);
        goodAddressStr = goodAddress.toString();
        testObject = new CoinURI(LTC, "litecoin:" + goodAddressStr + "?amount=12.34");
        assertEquals("12.34", GenericUtils.formatCoinValue(LTC, testObject.getAmount()));

        // Dogecoin
        goodAddress = BitAddress.from(DOGE, hash160);
        goodAddressStr = goodAddress.toString();
        testObject = new CoinURI(DOGE, "dogecoin:" + goodAddressStr + "?amount=12.34");
        assertEquals("12.34", GenericUtils.formatCoinValue(DOGE, testObject.getAmount()));

        // Peercoin
        goodAddress = BitAddress.from(PPC, hash160);
        goodAddressStr = goodAddress.toString();
        testObject = new CoinURI(PPC, "peercoin:" + goodAddressStr + "?amount=12.34");
        assertEquals("12.34", GenericUtils.formatCoinValue(PPC, testObject.getAmount()));

        // Darkcoin
        goodAddress = BitAddress.from(DASH, hash160);
        goodAddressStr = goodAddress.toString();
        testObject = new CoinURI(DASH, "dash:" + goodAddressStr + "?amount=12.34");
        assertEquals("12.34", GenericUtils.formatCoinValue(DASH, testObject.getAmount()));
    }

    @Test
    public void testGood_Simple() throws CoinURIParseException {
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS);
        assertNotNull(testObject);
        assertNull("Unexpected amount", testObject.getAmount());
        assertNull("Unexpected label", testObject.getLabel());
        assertNotNull(testObject.getAddress());
        assertEquals("Unexpected label", 20, getHash160(testObject.getAddress()).length);
    }

    /**
     * Test a broken URI (bad scheme)
     */
    @Test
    public void testBad_Scheme() {
        try {
            testObject = new CoinURI(BitcoinMain.get(), "blimpcoin:" + MAINNET_GOOD_ADDRESS);
            fail("Expecting BitcoinURIParseException");
        } catch (CoinURIParseException e) {
        }
    }

    /**
     * Test a broken URI (bad syntax)
     */
    @Test
    public void testBad_BadSyntax() {
        // Various illegal characters
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + "|" + MAINNET_GOOD_ADDRESS);
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("Bad URI syntax"));
        }

        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS + "\\");
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("Bad URI syntax"));
        }

        // Separator without field
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":");
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("Bad URI syntax"));
        }
    }

    /**
     * Test a broken URI (missing address)
     */
    @Test
    public void testBad_Address() {
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme());
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
        }
    }

    /**
     * Test a broken URI (bad address type)
     */
    @Test
    public void testBad_IncorrectAddressType() {
        try {
            testObject = new CoinURI(BitcoinTest.get(), BitcoinTest.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS);
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("Bad address"));
        }
    }

    /**
     * Handles a simple amount
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_Amount() throws CoinURIParseException {
        // Test the decimal parsing
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=6543210.12345678");
        assertEquals(654321012345678L, testObject.getAmount().value);

        // Test the decimal parsing
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=.12345678");
        assertEquals(12345678L, testObject.getAmount().value);

        // Test the integer parsing
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=6543210");
        assertEquals(654321000000000L, testObject.getAmount().value);
    }

    /**
     * Handles a simple label
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_Label() throws CoinURIParseException {
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?label=Hello%20World");
        assertEquals("Hello World", testObject.getLabel());
    }

    /**
     * Handles a simple label with an embedded ampersand and plus
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     * @throws UnsupportedEncodingException 
     */
    @Test
    public void testGood_LabelWithAmpersandAndPlus() throws Exception {
        String testString = "Hello Earth & Mars + Venus";
        String encodedLabel = CoinURI.encodeURLString(testString);
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS + "?label="
                + encodedLabel);
        assertEquals(testString, testObject.getLabel());
    }

    /**
     * Handles a Russian label (Unicode test)
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     * @throws UnsupportedEncodingException 
     */
    @Test
    public void testGood_LabelWithRussian() throws Exception {
        // Moscow in Russian in Cyrillic
        String moscowString = "\u041c\u043e\u0441\u043a\u0432\u0430";
        String encodedLabel = CoinURI.encodeURLString(moscowString); 
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS + "?label="
                + encodedLabel);
        assertEquals(moscowString, testObject.getLabel());
    }

    /**
     * Handles a simple message
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_Message() throws CoinURIParseException {
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?message=Hello%20World");
        assertEquals("Hello World", testObject.getMessage());
    }

    /**
     * Handles various well-formed combinations
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     */
    @Test
    public void testGood_Combinations() throws CoinURIParseException {
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=6543210&label=Hello%20World&message=Be%20well");
        assertEquals(
                "CoinURI['address'='1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH','amount'='6543210BTC','label'='Hello World','message'='Be well']",
                testObject.toString());
    }

    /**
     * Handles a badly formatted amount field
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     */
    @Test
    public void testBad_Amount() throws CoinURIParseException {
        // Missing
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                    + "?amount=");
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("amount"));
        }

        // Non-decimal (BIP 21)
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                    + "?amount=12X4");
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("amount"));
        }
    }

    @Test
    public void testEmpty_Label() throws CoinURIParseException {
        assertNull(new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?label=").getLabel());
    }

    @Test
    public void testEmpty_Message() throws CoinURIParseException {
        assertNull(new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?message=").getMessage());
    }

    /**
     * Handles duplicated fields (sneaky address overwrite attack)
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     */
    @Test
    public void testBad_Duplicated() throws CoinURIParseException {
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                    + "?address=aardvark");
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("address"));
        }
    }

    @Test
    public void testGood_ManyEquals() throws CoinURIParseException {
        assertEquals("aardvark=zebra", new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":"
                + MAINNET_GOOD_ADDRESS + "?label=aardvark=zebra").getLabel());
    }

    /**
     * Handles case when there are too many question marks
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     */
    @Test
    public void testBad_TooManyQuestionMarks() throws CoinURIParseException {
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                    + "?label=aardvark?message=zebra");
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("Too many question marks"));
        }
    }
    
    /**
     * Handles unknown fields (required and not required)
     * 
     * @throws CoinURIParseException
     *             If something goes wrong
     */
    @Test
    public void testUnknown() throws CoinURIParseException {
        // Unknown not required field
        testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?aardvark=true");
        assertEquals("CoinURI['address'='1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH','aardvark'='true']", testObject.toString());

        assertEquals("true", (String) testObject.getParameterByName("aardvark"));

        // Unknown not required field (isolated)
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                    + "?aardvark");
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("no separator"));
        }

        // Unknown and required field
        try {
            testObject = new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                    + "?req-aardvark=true");
            fail("Expecting CoinURIParseException");
        } catch (CoinURIParseException e) {
            assertTrue(e.getMessage().contains("req-aardvark"));
        }
    }

    @Test
    public void brokenURIs() throws CoinURIParseException {
        // Check we can parse the incorrectly formatted URIs produced by blockchain.info and its iPhone app.
        String str = "bitcoin://1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH?amount=0.01000000";
        CoinURI uri = new CoinURI(str);
        assertEquals("1KzTSfqjF2iKCduwz59nv2uqh1W2JsTxZH", uri.getAddress().toString());
        assertEquals(BTC.value(Coin.CENT), uri.getAmount());
    }

    @Test(expected = CoinURIParseException.class)
    public void testBad_AmountTooPrecise() throws CoinURIParseException {
        new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=0.123456789");
    }

    @Test(expected = CoinURIParseException.class)
    public void testBad_NegativeAmount() throws CoinURIParseException {
        new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?amount=-1");
    }

//    @Test(expected = CoinURIParseException.class)
//    public void testBad_TooLargeAmount() throws CoinURIParseException {
//        new CoinURI(BitcoinMain.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
//                + "?amount=100000000");
//    }

    @Test
    public void testPaymentProtocolReq() throws Exception {
        // Non-backwards compatible form ...
        CoinURI uri = new CoinURI(BTC, "bitcoin:?r=https%3A%2F%2Fbitcoincore.org%2F%7Egavin%2Ff.php%3Fh%3Db0f02e7cea67f168e25ec9b9f9d584f9");
        assertEquals("https://bitcoincore.org/~gavin/f.php?h=b0f02e7cea67f168e25ec9b9f9d584f9", uri.getPaymentRequestUrl());
        assertNull(uri.getAddress());

        // Non-backwards compatible form ...
        uri = new CoinURI("bitcoin:?r=https%3A%2F%2Fbitcoincore.org%2F%7Egavin%2Ff.php%3Fh%3Db0f02e7cea67f168e25ec9b9f9d584f9");
        assertEquals("https://bitcoincore.org/~gavin/f.php?h=b0f02e7cea67f168e25ec9b9f9d584f9", uri.getPaymentRequestUrl());
//        assertEquals(BTC, uri.getType());
        assertNull(uri.getAddress());
    }

    @Test
    public void testAddressRequest() throws Exception {
        CoinURI uri = new CoinURI(BTC, "bitcoin:?req-addressrequest=https%3A%2F%2Fcoinomi.com");
        assertTrue(uri.isAddressRequest());
        assertNull(uri.getAddress());
        assertEquals(BTC, uri.getType());
        assertEquals("https://coinomi.com", uri.getAddressRequestUri().toString());
        assertEquals("https://coinomi.com?address=" + MAINNET_GOOD_ADDRESS,
                uri.getAddressRequestUriResponse(MAINNET_GOOD_ADDRESS).toString());

        // Reply URI has path and query
        uri = new CoinURI("bitcoin:?req-addressrequest=https%3A%2F%2Fcoinomi.com%2Fsome-path%3Fabc%3Dxyz");
        assertTrue(uri.isAddressRequest());
        assertNull(uri.getAddress());
        assertEquals(BTC, uri.getType());
        assertEquals("https://coinomi.com/some-path?abc=xyz", uri.getAddressRequestUri().toString());
        assertEquals("https://coinomi.com/some-path?abc=xyz&address=" + MAINNET_GOOD_ADDRESS,
                uri.getAddressRequestUriResponse(MAINNET_GOOD_ADDRESS).toString());

        // Reply is a generic URI
        uri = new CoinURI("bitcoin:?req-addressrequest=myscheme%3A%2F%2Fmyaction");
        assertTrue(uri.isAddressRequest());
        assertNull(uri.getAddress());
        assertEquals(BTC, uri.getType());
        assertEquals("myscheme://myaction", uri.getAddressRequestUri().toString());
        assertEquals("myscheme://myaction?address=" + MAINNET_GOOD_ADDRESS,
                uri.getAddressRequestUriResponse(MAINNET_GOOD_ADDRESS).toString());


        byte[] hash160 = BitAddress.from(BitcoinMain.get(), MAINNET_GOOD_ADDRESS).getHash160();
        String goodAddressStr;

        // Testnet
        goodAddressStr = BitAddress.from(BTC_TEST, hash160).toString();
        uri = new CoinURI("bitcoin:?req-addressrequest=https%3A%2F%2Fcoinomi.com&req-network=test");
        assertTrue(uri.isAddressRequest());
        assertNull(uri.getAddress());
        assertEquals(BTC_TEST, uri.getType());
        assertEquals("https://coinomi.com", uri.getAddressRequestUri().toString());
        assertEquals("https://coinomi.com?address=" + goodAddressStr,
                uri.getAddressRequestUriResponse(goodAddressStr).toString());

        // NuBits
        goodAddressStr = BitAddress.from(NBT, hash160).toString();
        uri = new CoinURI("nu:?req-addressrequest=https%3A%2F%2Fcoinomi.com");
        assertTrue(uri.isAddressRequest());
        assertNull(uri.getAddress());
        assertEquals(NBT, uri.getType());
        assertEquals("https://coinomi.com", uri.getAddressRequestUri().toString());
        assertEquals("https://coinomi.com?address=" + goodAddressStr,
                uri.getAddressRequestUriResponse(goodAddressStr).toString());

        // NuShares
        goodAddressStr = BitAddress.from(NSR, hash160).toString();
        uri = new CoinURI("nu:?req-addressrequest=https%3A%2F%2Fcoinomi.com&req-network=nsr.main");
        assertTrue(uri.isAddressRequest());
        assertNull(uri.getAddress());
        assertEquals(NSR, uri.getType());
        assertEquals("https://coinomi.com", uri.getAddressRequestUri().toString());
        assertEquals("https://coinomi.com?address=" + goodAddressStr,
                uri.getAddressRequestUriResponse(goodAddressStr).toString());
    }

    @Test(expected = CoinURIParseException.class)
    public void testAddressRequestBadFormat() throws Exception {
        new CoinURI(BitcoinTest.get(), BitcoinMain.get().getUriScheme() + ":" + MAINNET_GOOD_ADDRESS
                + "?req-addressrequest=https%3A%2F%2Fcoinomi.com");
    }
}
