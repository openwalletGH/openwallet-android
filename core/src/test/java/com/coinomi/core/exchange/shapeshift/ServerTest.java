package com.coinomi.core.exchange.shapeshift;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftAmountTx;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftCoin;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftCoins;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftEmail;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftException;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftLimit;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftMarketInfo;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftNormalTx;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftRate;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftTime;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftTxStatus;
import com.coinomi.core.wallet.AbstractAddress;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author John L. Jegutanis
 */
public class ServerTest {

    final CoinType BTC = BitcoinMain.get();
    final CoinType LTC = LitecoinMain.get();
    final CoinType DOGE = DogecoinMain.get();
    final CoinType NBT = NuBitsMain.get();

    private MockWebServer server;
    private ShapeShift shapeShift;

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();
        server.start();

        shapeShift = new ShapeShift();
        shapeShift.baseUrl = server.getUrl("/").toString();
        shapeShift.client.setConnectionSpecs(Collections.singletonList(ConnectionSpec.CLEARTEXT));
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void testGetCoins() throws ShapeShiftException, IOException, InterruptedException, JSONException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(GET_COINS_JSON));

        ShapeShiftCoins coinsReply = shapeShift.getCoins();
        assertFalse(coinsReply.isError);
        assertEquals(3, coinsReply.coins.size());
        assertEquals(1, coinsReply.availableCoinTypes.size());
        assertEquals(BTC, coinsReply.availableCoinTypes.get(0));
        JSONObject coinsJson = new JSONObject(GET_COINS_JSON);
        for (ShapeShiftCoin coin : coinsReply.coins) {
            JSONObject json = coinsJson.getJSONObject(coin.symbol);
            assertEquals(json.getString("name"), coin.name);
            assertEquals(json.getString("symbol"), coin.symbol);
            assertEquals(json.getString("image"), coin.image.toString());
            assertEquals(json.getString("status").equals("available"), coin.isAvailable);
        }

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/getcoins", request.getPath());
    }

    @Test
    public void testGetMarketInfo() throws ShapeShiftException, IOException, InterruptedException, JSONException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(MARKET_INFO_BTC_NBT_JSON));

        ShapeShiftMarketInfo marketInfoReply = shapeShift.getMarketInfo(BTC, NBT);
        assertFalse(marketInfoReply.isError);
        assertEquals("btc_nbt", marketInfoReply.pair);
        assertNotNull(marketInfoReply.rate);

        assertNotNull(marketInfoReply.rate);
        assertNotNull(marketInfoReply.limit);
        assertNotNull(marketInfoReply.minimum);

        assertEquals(NBT.value("99.99"), marketInfoReply.rate.convert(BTC.value("1")));
        assertEquals(BTC.value("4"), marketInfoReply.limit);
        assertEquals(BTC.value("0.00000104"), marketInfoReply.minimum);

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/marketinfo/btc_nbt", request.getPath());
    }

    @Test
    public void testGetRate() throws ShapeShiftException, IOException, InterruptedException, JSONException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(GET_RATE_BTC_LTC_JSON));

        ShapeShiftRate rateReply = shapeShift.getRate(BTC, LTC);
        assertFalse(rateReply.isError);
        assertEquals("btc_ltc", rateReply.pair);
        assertNotNull(rateReply.rate);

        assertEquals(LTC, rateReply.rate.convert(BTC.oneCoin()).type);
        assertEquals(BTC, rateReply.rate.convert(LTC.oneCoin()).type);

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/rate/btc_ltc", request.getPath());
    }

    @Test
    public void testGetLimit() throws ShapeShiftException, IOException, InterruptedException, JSONException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(GET_LIMIT_BTC_LTC_JSON));

        ShapeShiftLimit limitReply = shapeShift.getLimit(BTC, LTC);
        assertFalse(limitReply.isError);
        assertEquals("btc_ltc", limitReply.pair);
        assertNotNull(limitReply.limit);

        assertEquals(BTC, limitReply.limit.type);
        assertEquals("5", limitReply.limit.toPlainString());

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/limit/btc_ltc", request.getPath());
    }

    @Test
    public void testGetTime() throws ShapeShiftException, IOException, InterruptedException, JSONException, AddressMalformedException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(GET_TIME_PENDING_JSON));
        server.enqueue(new MockResponse().setBody(GET_TIME_EXPIRED_JSON));

        AbstractAddress address = NuBitsMain.get().newAddress("BPjxHqswNZB5vznbrAAxi5zGVq3ruhtBU8");

        ShapeShiftTime timeReply = shapeShift.getTime(address);
        assertFalse(timeReply.isError);
        assertEquals(ShapeShiftTime.Status.PENDING, timeReply.status);
        assertEquals(100, timeReply.secondsRemaining);

        timeReply = shapeShift.getTime(address);
        assertFalse(timeReply.isError);
        assertEquals(ShapeShiftTime.Status.EXPIRED, timeReply.status);
        assertEquals(0, timeReply.secondsRemaining);

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/timeremaining/BPjxHqswNZB5vznbrAAxi5zGVq3ruhtBU8", request.getPath());
        request = server.takeRequest();
        assertEquals("/timeremaining/BPjxHqswNZB5vznbrAAxi5zGVq3ruhtBU8", request.getPath());
    }

    @Test
    public void testGetTxStatus() throws ShapeShiftException, IOException, InterruptedException, JSONException, AddressMalformedException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(TX_STATUS_NO_DEPOSIT_JSON));
        server.enqueue(new MockResponse().setBody(TX_STATUS_RECEIVED_JSON));
        server.enqueue(new MockResponse().setBody(TX_STATUS_NEW_STATUS_JSON));
        server.enqueue(new MockResponse().setBody(TX_STATUS_COMPLETE_JSON));
        server.enqueue(new MockResponse().setBody(TX_STATUS_FAILED_JSON));

        AbstractAddress address = BTC.newAddress("1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN");

        ShapeShiftTxStatus txStatusReply = shapeShift.getTxStatus(address);
        assertFalse(txStatusReply.isError);
        assertEquals(ShapeShiftTxStatus.Status.NO_DEPOSITS, txStatusReply.status);
        assertEquals(address, txStatusReply.address);

        txStatusReply = shapeShift.getTxStatus(address);
        assertFalse(txStatusReply.isError);
        assertEquals(ShapeShiftTxStatus.Status.RECEIVED, txStatusReply.status);
        assertEquals(address, txStatusReply.address);
        assertEquals("0.00297537", txStatusReply.incomingValue.toPlainString());
        assertEquals(BTC, txStatusReply.incomingValue.type);

        txStatusReply = shapeShift.getTxStatus(address);
        assertFalse(txStatusReply.isError);
        assertEquals(ShapeShiftTxStatus.Status.UNKNOWN, txStatusReply.status);

        txStatusReply = shapeShift.getTxStatus(address);
        assertFalse(txStatusReply.isError);
        assertEquals(ShapeShiftTxStatus.Status.COMPLETE, txStatusReply.status);
        assertEquals(address, txStatusReply.address);
        assertEquals("0.00297537", txStatusReply.incomingValue.toPlainString());
        assertEquals(BTC, txStatusReply.incomingValue.type);
        assertEquals("LMmeBWH17TWkQKvK7YFio2oiimPAzrHG6f", txStatusReply.withdraw.toString());
        assertEquals(LTC, txStatusReply.withdraw.getType());
        assertEquals("0.42", txStatusReply.outgoingValue.toPlainString());
        assertEquals(LTC, txStatusReply.outgoingValue.type);
        assertEquals("66fa0b4c11227f9f05efa13d23e58c65b50acbd6395a126b5cd751064e6e79df",
                txStatusReply.transactionId);

        txStatusReply = shapeShift.getTxStatus(address);
        assertTrue(txStatusReply.isError);
        assertEquals(ShapeShiftTxStatus.Status.FAILED, txStatusReply.status);
        assertEquals("error", txStatusReply.errorMessage);

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/txStat/1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", request.getPath());
        request = server.takeRequest();
        assertEquals("/txStat/1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", request.getPath());
        request = server.takeRequest();
        assertEquals("/txStat/1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", request.getPath());
        request = server.takeRequest();
        assertEquals("/txStat/1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", request.getPath());
    }

    @Test
    public void testNormalTransaction() throws ShapeShiftException, IOException, InterruptedException, JSONException, AddressMalformedException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(NORMAL_TRANSACTION_JSON));

        AbstractAddress withdrawal = DOGE.newAddress("DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV");
        AbstractAddress refund = BTC.newAddress("1Nz4xHJjNCnZFPjRUq8CN4BZEXTgLZfeUW");
        ShapeShiftNormalTx normalTxReply = shapeShift.exchange(withdrawal, refund);
        assertFalse(normalTxReply.isError);
        assertEquals("btc_doge", normalTxReply.pair);
        assertEquals("18ETaXCYhJ8sxurh41vpKC3E6Tu7oJ94q8", normalTxReply.deposit.toString());
        assertEquals(BTC, normalTxReply.deposit.getType());
        assertEquals(withdrawal.toString(), normalTxReply.withdrawal.toString());
        assertEquals(DOGE, normalTxReply.withdrawal.getType());

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/shift", request.getPath());
        JSONObject reqJson = new JSONObject(request.getBody().readUtf8());
        assertEquals(withdrawal.toString(), reqJson.getString("withdrawal"));
        assertEquals(refund.toString(), reqJson.getString("returnAddress"));
        assertEquals("btc_doge", reqJson.getString("pair"));
    }


    @Test
    public void testFixedAmountTransaction() throws ShapeShiftException, IOException,
            InterruptedException, JSONException, AddressMalformedException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(FIXED_AMOUNT_TRANSACTION_JSON));

        AbstractAddress withdrawal = DOGE.newAddress("DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV");
        AbstractAddress refund = BTC.newAddress("1Nz4xHJjNCnZFPjRUq8CN4BZEXTgLZfeUW");
        Value amount = DOGE.value("1000");
        ShapeShiftAmountTx amountTxReply = shapeShift.exchangeForAmount(amount, withdrawal, refund);
        assertFalse(amountTxReply.isError);
        assertEquals("btc_doge", amountTxReply.pair);

        assertEquals("14gQ3xywKEUA6CfH61F8t2c6oB5nLnUjL5", amountTxReply.deposit.toString());
        assertEquals(BTC, amountTxReply.deposit.getType());
        assertEquals("0.00052379", amountTxReply.depositAmount.toPlainString());
        assertEquals(BTC, amountTxReply.depositAmount.type);

        assertEquals(withdrawal.toString(), amountTxReply.withdrawal.toString());
        assertEquals(DOGE, amountTxReply.withdrawal.getType());
        assertEquals(amount.toPlainString(), amountTxReply.withdrawalAmount.toPlainString());
        assertEquals(DOGE, amountTxReply.withdrawalAmount.type);

        assertEquals(1427149038191L, amountTxReply.expiration.getTime());
        assertEquals(BTC.value(".00052379"), amountTxReply.rate.convert(Value.parse(DOGE, "1000")));

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/sendamount", request.getPath());
        JSONObject reqJson = new JSONObject(request.getBody().readUtf8());
        assertEquals(withdrawal.toString(), reqJson.getString("withdrawal"));
        assertEquals(refund.toString(), reqJson.getString("returnAddress"));
        assertEquals("btc_doge", reqJson.getString("pair"));
        assertEquals(amount.toPlainString(), reqJson.getString("amount"));
    }

    @Test
    public void testEmail() throws ShapeShiftException, IOException, InterruptedException,
            JSONException, AddressMalformedException {
        // Schedule some responses.
        server.enqueue(new MockResponse().setBody(TX_STATUS_COMPLETE_JSON));
        server.enqueue(new MockResponse().setBody(EMAIL_JSON));

        ShapeShiftTxStatus txStatusReply =
                shapeShift.getTxStatus(BTC.newAddress("1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN"));
        ShapeShiftEmail emailReply =
                shapeShift.requestEmailReceipt("mail@example.com", txStatusReply);
        assertFalse(emailReply.isError);
        assertEquals(ShapeShiftEmail.Status.SUCCESS, emailReply.status);
        assertEquals("Email receipt sent", emailReply.message);

        // Optional: confirm that your app made the HTTP requests you were expecting.
        RecordedRequest request = server.takeRequest();
        assertEquals("/txStat/1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", request.getPath());
        request = server.takeRequest();
        assertEquals("/mail", request.getPath());
        JSONObject reqJson = new JSONObject(request.getBody().readUtf8());
        assertEquals("mail@example.com", reqJson.getString("email"));
        assertEquals("66fa0b4c11227f9f05efa13d23e58c65b50acbd6395a126b5cd751064e6e79df",
                reqJson.getString("txid"));
    }

    @Test(expected = ShapeShiftException.class)
    public void testEmailFail() throws ShapeShiftException, IOException, InterruptedException,
            JSONException, AddressMalformedException {
        server.enqueue(new MockResponse().setBody(TX_STATUS_NO_DEPOSIT_JSON));
        ShapeShiftTxStatus txStatusReply = shapeShift
                .getTxStatus(BTC.newAddress("1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN"));
        // Bad status
        shapeShift.requestEmailReceipt("mail@example.com", txStatusReply);
    }

    @Test(expected = ShapeShiftException.class)
    public void testGetMarketInfoFail() throws ShapeShiftException, IOException {
        server.enqueue(new MockResponse().setBody(MARKET_INFO_BTC_NBT_JSON));
        // Incorrect pair
        shapeShift.getMarketInfo(BTC, LTC);
    }

    @Test(expected = ShapeShiftException.class)
    public void testGetRateFail() throws ShapeShiftException, IOException {
        server.enqueue(new MockResponse().setBody(GET_RATE_BTC_LTC_JSON));
        // Incorrect pair
        shapeShift.getRate(NBT, LTC);
    }

    @Test(expected = ShapeShiftException.class)
    public void testGetLimitFail() throws ShapeShiftException, IOException {
        server.enqueue(new MockResponse().setBody(GET_LIMIT_BTC_LTC_JSON));
        // Incorrect pair
        shapeShift.getLimit(LTC, DOGE);
    }

    @Test(expected = ShapeShiftException.class)
    public void testGetTxStatusFail() throws ShapeShiftException, AddressMalformedException, IOException {
        server.enqueue(new MockResponse().setBody(TX_STATUS_COMPLETE_JSON));
        // Used an incorrect address, correct is 1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN
        shapeShift.getTxStatus(BTC.newAddress("18ETaXCYhJ8sxurh41vpKC3E6Tu7oJ94q8"));
    }

    @Test(expected = ShapeShiftException.class)
    public void testNormalTransactionFail() throws ShapeShiftException, AddressMalformedException, IOException {
        server.enqueue(new MockResponse().setBody(NORMAL_TRANSACTION_JSON));
        // Incorrect Dogecoin address, correct is DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV
        shapeShift.exchange(DOGE.newAddress("DSntbp199h851m3Y1g3ruYCQHzWYCZQmmA"),
                BTC.newAddress("1Nz4xHJjNCnZFPjRUq8CN4BZEXTgLZfeUW"));
    }

    @Test(expected = ShapeShiftException.class)
    public void testFixedAmountTransactionFail()
            throws ShapeShiftException, AddressMalformedException, IOException {
        server.enqueue(new MockResponse().setBody(FIXED_AMOUNT_TRANSACTION_JSON));
        // We withdraw Dogecoins to a Bitcoin address
        shapeShift.exchangeForAmount(DOGE.value("1000"),
                BTC.newAddress("18ETaXCYhJ8sxurh41vpKC3E6Tu7oJ94q8"),
                BTC.newAddress("1Nz4xHJjNCnZFPjRUq8CN4BZEXTgLZfeUW"));
    }

    @Test(expected = ShapeShiftException.class)
    public void testFixedAmountTransactionFail2()
            throws ShapeShiftException, AddressMalformedException, IOException {
        server.enqueue(new MockResponse().setBody(FIXED_AMOUNT_TRANSACTION_JSON));
        // Incorrect Dogecoin address, correct is DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV
        shapeShift.exchangeForAmount(DOGE.value("1000"),
                DOGE.newAddress("DSntbp199h851m3Y1g3ruYCQHzWYCZQmmA"),
                BTC.newAddress("1Nz4xHJjNCnZFPjRUq8CN4BZEXTgLZfeUW"));
    }

    @Test(expected = ShapeShiftException.class)
    public void testFixedAmountTransactionFail3()
            throws ShapeShiftException, AddressMalformedException, IOException {
        server.enqueue(new MockResponse().setBody(FIXED_AMOUNT_TRANSACTION_JSON));
        // Incorrect amount, correct is 1000
        shapeShift.exchangeForAmount(DOGE.value("1"),
                DOGE.newAddress("DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV"),
                BTC.newAddress("1Nz4xHJjNCnZFPjRUq8CN4BZEXTgLZfeUW"));
    }

    public static final String GET_COINS_JSON =
            "{" +
            "BTC: {" +
            "name: \"Bitcoin\"," +
            "symbol: \"BTC\"," +
            "image: \"https://shapeshift.io/images/coins/bitcoin.png\"," +
            "status: \"available\"" +
            "}," +
            "LTC: {" +
            "name: \"Litecoin\"," +
            "symbol: \"LTC\"," +
            "image: \"https://shapeshift.io/images/coins/litecoin.png\"," +
            "status: \"unavailable\"" +
            "}," +
            "UNSUPPORTED: {" +
            "name: \"UnsupportedCoin\"," +
            "symbol: \"UNSUPPORTED\"," +
            "image: \"https://shapeshift.io/images/coins/UnsupportedCoin.png\"," +
            "status: \"available\"" +
            "}" +
            "}";
    public static final String MARKET_INFO_BTC_NBT_JSON = "{" +
            "\"pair\" : \"btc_nbt\"," +
            "\"rate\" : \"100\"," +
            "\"minerFee\" : \"0.01\"," +
            "\"limit\" : \"4\"," +
            "\"minimum\" : 0.00000104" +
            "}";
    public static final String GET_RATE_BTC_LTC_JSON = "{" +
            "\"pair\" : \"btc_ltc\"," +
            "\"rate\" : \"100\"" +
            "}";
    public static final String GET_LIMIT_BTC_LTC_JSON = "{" +
            "\"pair\" : \"btc_ltc\"," +
            "\"limit\" : \"5\"," +
            "\"min\" : 0.00004008" +
            "}";
    public static final String GET_TIME_PENDING_JSON = "{" +
            "status: \"pending\"," +
            "seconds_remaining: \"100\"" +
            "}";
    public static final String GET_TIME_EXPIRED_JSON = "{" +
            "status: \"expired\"," +
            "seconds_remaining: \"0\"" +
            "}";
    public static final String TX_STATUS_NO_DEPOSIT_JSON = "{" +
            "status: \"no_deposits\"," +
            "address: \"1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN\"" +
            "}";
    public static final String TX_STATUS_RECEIVED_JSON = "{" +
            "status: \"received\"," +
            "address: \"1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN\"," +
            "incomingCoin: 0.00297537," +
            "incomingType: \"BTC\"" +
            "}";
    public static final String TX_STATUS_NEW_STATUS_JSON = "{" +
            "status: \"some_new_optional_status\"," +
            "address: \"1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN\"," +
            "incomingCoin: 0.00297537," +
            "incomingType: \"BTC\"" +
            "}";
    public static final String TX_STATUS_COMPLETE_JSON = "{" +
            "status: \"complete\"," +
            "address: \"1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN\"," +
            "withdraw: \"LMmeBWH17TWkQKvK7YFio2oiimPAzrHG6f\"," +
            "incomingCoin: 0.00297537," +
            "incomingType: \"BTC\"," +
            "outgoingCoin: \"0.42000000\"," +
            "outgoingType: \"LTC\"," +
            "transaction: \"66fa0b4c11227f9f05efa13d23e58c65b50acbd6395a126b5cd751064e6e79df\"" +
            "}";
    public static final String TX_STATUS_FAILED_JSON = "{" +
            "status: \"failed\"," +
            "error: \"error\"" +
            "}";
    public static final String NORMAL_TRANSACTION_JSON = "{" +
            "\"deposit\":\"18ETaXCYhJ8sxurh41vpKC3E6Tu7oJ94q8\"," +
            "\"depositType\":\"BTC\"," +
            "\"withdrawal\":\"DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV\"," +
            "\"withdrawalType\":\"DOGE\"" +
            "}";
    public static final String FIXED_AMOUNT_TRANSACTION_JSON = "{" +
            "\"success\":{" +
            "\"pair\":\"btc_doge\"," +
            "\"withdrawal\":\"DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV\"," +
            "\"withdrawalAmount\":\"1000\"," +
            "\"minerFee\":\"1\"," +
            "\"deposit\":\"14gQ3xywKEUA6CfH61F8t2c6oB5nLnUjL5\"," +
            "\"depositAmount\":\"0.00052379\"," +
            "\"expiration\":1427149038191," +
            "\"quotedRate\":\"1911057.69230769\"," +
            "}" +
            "}";
    public static final String EMAIL_JSON = "{" +
            "\"email\":{" +
            "\"status\":\"success\"," +
            "\"message\":\"Email receipt sent\"" +
            "}" +
            "}";
}
