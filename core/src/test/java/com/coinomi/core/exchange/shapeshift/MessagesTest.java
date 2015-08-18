package com.coinomi.core.exchange.shapeshift;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.coins.NuBitsMain;
import com.coinomi.core.coins.PeercoinMain;
import com.coinomi.core.coins.Value;
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

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * @author John L. Jegutanis
 */
public class MessagesTest {
    final CoinType BTC = BitcoinMain.get();
    final CoinType LTC = LitecoinMain.get();
    final CoinType DOGE = DogecoinMain.get();
    final CoinType NBT = NuBitsMain.get();
    final CoinType PPC = PeercoinMain.get();
    final Value ONE_BTC = BTC.oneCoin();

    @Test
    public void testCoins() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
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
                "}");
        ShapeShiftCoins coins = new ShapeShiftCoins(json);
        assertNotNull(coins);
        assertFalse(coins.isError);
        assertNotNull(coins.coins);
        assertEquals(3, coins.coins.size());
        // LTC is unavailable and UNSUPPORTED is unsupported
        assertEquals(1, coins.availableCoinTypes.size());
        assertEquals(BTC, coins.availableCoinTypes.get(0));

        for (ShapeShiftCoin coin : coins.coins) {
            JSONObject expected = json.getJSONObject(coin.symbol);
            assertEquals(expected.getString("name"), coin.name);
            assertEquals(expected.getString("symbol"), coin.symbol);
            assertEquals(expected.getString("image"), coin.image.toString());
            assertEquals(expected.getString("status").equals("available"), coin.isAvailable);
        }
    }

    @Test
    public void testCoin() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "name: \"Bitcoin\"," +
                    "symbol: \"BTC\"," +
                    "image: \"https://shapeshift.io/images/coins/bitcoin.png\"," +
                    "status: \"available\"" +
                "}");
        ShapeShiftCoin coin = new ShapeShiftCoin(json);
        assertNotNull(coin);
        assertFalse(coin.isError);
        assertEquals("Bitcoin", coin.name);
        assertEquals("BTC", coin.symbol);
        assertEquals("https://shapeshift.io/images/coins/bitcoin.png", coin.image.toString());
        assertTrue(coin.isAvailable);
    }


    @Test
    public void testMarketInfo() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                        "\"pair\" : \"btc_nbt\"," +
                        "\"rate\" : \"100\"," +
                        "\"minerFee\" : \"0.01\"," +
                        "\"limit\" : \"4\"," +
                        "\"minimum\" : 0.00000104" +
                        "}");
        ShapeShiftMarketInfo marketInfo = new ShapeShiftMarketInfo(json);
        assertNotNull(marketInfo);
        assertFalse(marketInfo.isError);
        assertEquals("btc_nbt", marketInfo.pair);
        assertTrue(marketInfo.isPair("BTC_NBT"));
        assertTrue(marketInfo.isPair("btc_nbt"));
        assertTrue(marketInfo.isPair(BTC, NBT));
        assertFalse(marketInfo.isPair("doge_ltc"));
        assertFalse(marketInfo.isPair(DOGE, LTC));
        assertNotNull(marketInfo.rate);
        assertNotNull(marketInfo.limit);
        assertNotNull(marketInfo.minimum);

        assertEquals(NBT.value("99.99"), marketInfo.rate.convert(BTC.value("1")));
        assertEquals(BTC.value("4"), marketInfo.limit);
        assertEquals(BTC.value("0.00000104"), marketInfo.minimum);
    }

    @Test
    public void testMarketInfo2() throws JSONException, ShapeShiftException {
        ShapeShiftMarketInfo info = new ShapeShiftMarketInfo(new JSONObject(
                "{\n" +
                        "pair: \"ppc_btc\",\n" +
                        "rate: 0.00098678,\n" +
                        "minerFee: 0.0001,\n" +
                        "limit: 2162.11925969,\n" +
                        "minimum: 0.17391304\n" +
                        "}"));
        assertEquals(BTC.value("0.00098678").subtract("0.0001"), info.rate.convert(PPC.value("1")));
        assertEquals(PPC.value("2162.119259"), info.limit);
        assertEquals(PPC.value("0.173914"), info.minimum);

        info = new ShapeShiftMarketInfo(new JSONObject(
                "{\n" +
                        "pair: \"btc_ppc\",\n" +
                        "rate: 866.73913043,\n" +
                        "minerFee: 0.01,\n" +
                        "limit: 1.1185671,\n" +
                        "minimum: 0.0000198\n" +
                        "}"));
        assertEquals(PPC.value("866.739130").subtract("0.01"), info.rate.convert(BTC.value("1")));
        assertEquals(BTC.value("1.1185671"), info.limit);
        assertEquals(BTC.value("0.0000198"), info.minimum);

        info = new ShapeShiftMarketInfo(new JSONObject(
                "{\n" +
                        "pair: \"btc_nbt\",\n" +
                        "rate: 226.39568082,\n" +
                        "minerFee: 0.01,\n" +
                        "limit: 3.70968678,\n" +
                        "minimum: 0.00008692\n" +
                        "}"));
        assertEquals(NBT.value("226.3957").subtract("0.01"), info.rate.convert(BTC.value("1")));
        assertEquals(BTC.value("3.70968678"), info.limit);
        assertEquals(BTC.value("0.00008692"), info.minimum);

        info = new ShapeShiftMarketInfo(new JSONObject(
                "{\n" +
                        "pair: \"nbt_btc\",\n" +
                        "rate: 0.00433171,\n" +
                        "minerFee: 0.0001,\n" +
                        "limit: 1021.50337123,\n" +
                        "minimum: 0.04542677\n" +
                        "}"));
        assertEquals(BTC.value("0.00433171").subtract("0.0001"), info.rate.convert(NBT.value("1")));
        assertEquals(NBT.value("1021.5033"), info.limit);
        assertEquals(NBT.value("0.0455"), info.minimum);
    }

    @Test
    public void testRateWithoutMinerFee() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "\"pair\" : \"btc_ltc\"," +
                    "\"rate\" : \"100\"" +
                "}");
        ShapeShiftRate rate = new ShapeShiftRate(json);
        assertNotNull(rate);
        assertFalse(rate.isError);
        assertEquals("btc_ltc", rate.pair);
        assertNotNull(rate.rate);

        assertEquals(LTC.value("100"), rate.rate.convert(BTC.value("1")));
        assertEquals(LTC.value("1"), rate.rate.convert(BTC.value("0.01")));
        assertEquals(BTC.value("0.01"), rate.rate.convert(LTC.value("1")));
        assertEquals(BTC.value("1"), rate.rate.convert(LTC.value("100")));
    }

    @Test
    public void testRateWithoutMinerFee2() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "\"pair\" : \"btc_nbt\"," +
                    "\"rate\" : \"123.456789\"" +
                "}");
        ShapeShiftRate rate = new ShapeShiftRate(json);
        assertNotNull(rate);
        assertFalse(rate.isError);
        assertEquals("btc_nbt", rate.pair);
        assertNotNull(rate.rate);

        assertEquals(NBT, rate.rate.convert(ONE_BTC).type);
        assertEquals(BTC, rate.rate.convert(NBT.oneCoin()).type);
    }

    @Test
    public void testRateWithMinerFee() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "\"pair\" : \"btc_nbt\"," +
                    "\"rate\" : \"100\"," +
                    "\"minerFee\" : \"0.01\"" +
                "}");
        ShapeShiftRate rate = new ShapeShiftRate(json);
        assertNotNull(rate);
        assertFalse(rate.isError);
        assertEquals("btc_nbt", rate.pair);
        assertNotNull(rate.rate);

        assertEquals(NBT.value("99.99"), rate.rate.convert(BTC.value("1")));
        assertEquals(BTC.value("1"), rate.rate.convert(NBT.value("99.99")));
    }

    @Test
    public void testLimit() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "\"pair\" : \"ltc_doge\"," +
                    "\"limit\" : \"200\"," +
                    "\"min\" : 0.00014772" +
                "}");
        ShapeShiftLimit limit = new ShapeShiftLimit(json);
        assertNotNull(limit);
        assertFalse(limit.isError);
        assertEquals("ltc_doge", limit.pair);
        assertNotNull(limit.limit);
        assertNotNull(limit.minimum);

        assertEquals(LTC.value("200"), limit.limit);
        assertEquals(LTC.value("0.00014772"), limit.minimum);
    }

    @Test
    public void testLimit2() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "pair: \"nbt_btc\"," +
                    "limit: \"1015.15359146\"," +
                    "min: 0.053118518219312046" +
                "}");
        ShapeShiftLimit limit = new ShapeShiftLimit(json);
        assertNotNull(limit);
        assertFalse(limit.isError);
        assertEquals("nbt_btc", limit.pair);
        assertNotNull(limit.limit);
        assertNotNull(limit.minimum);

        assertEquals(NBT.value("1015.1535"), limit.limit);
        assertEquals(NBT.value("0.0532"), limit.minimum);
    }

    @Test
    public void testTime() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "status: \"pending\"," +
                    "seconds_remaining: \"100\"" +
                "}");
        ShapeShiftTime time = new ShapeShiftTime(json);
        assertNotNull(time);
        assertFalse(time.isError);
        assertEquals(ShapeShiftTime.Status.PENDING, time.status);
        assertEquals(100, time.secondsRemaining);
    }

    @Test
    public void testTime2() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "status: \"expired\"," +
                    "seconds_remaining: \"0\"" +
                "}");
        ShapeShiftTime time = new ShapeShiftTime(json);
        assertNotNull(time);
        assertFalse(time.isError);
        assertEquals(ShapeShiftTime.Status.EXPIRED, time.status);
        assertEquals(0, time.secondsRemaining);
    }

    @Test
    public void testTxStatus() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "status: \"no_deposits\"," +
                    "address: \"1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN\"" +
                "}");
        ShapeShiftTxStatus txStatus = new ShapeShiftTxStatus(json);
        assertNotNull(txStatus);
        assertFalse(txStatus.isError);
        assertEquals(ShapeShiftTxStatus.Status.NO_DEPOSITS, txStatus.status);
//        assertEquals("1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", txStatus.address.toString());
        assertNull(txStatus.withdraw);
        assertNull(txStatus.incomingValue);
        assertNull(txStatus.outgoingValue);
        assertNull(txStatus.transactionId);
    }

    @Test
    public void testTxStatus2() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "status: \"received\"," +
                    "address: \"1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN\"," +
                    "incomingCoin: 0.00297537," +
                    "incomingType: \"BTC\"" +
                "}");
        ShapeShiftTxStatus txStatus = new ShapeShiftTxStatus(json);
        assertNotNull(txStatus);
        assertFalse(txStatus.isError);
        assertEquals(ShapeShiftTxStatus.Status.RECEIVED, txStatus.status);
        assertEquals("1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", txStatus.address.toString());
        assertEquals(BTC, txStatus.address.getType());
        assertNull(txStatus.withdraw);
        assertEquals(BTC.value("0.00297537"), txStatus.incomingValue);
        assertNull(txStatus.outgoingValue);
        assertNull(txStatus.transactionId);
    }

    @Test
    public void testTxStatus3() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                        "status: \"complete\"," +
                        "address: \"1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN\"," +
                        "withdraw: \"LMmeBWH17TWkQKvK7YFio2oiimPAzrHG6f\"," +
                        "incomingCoin: 0.00297537," +
                        "incomingType: \"BTC\"," +
                        "outgoingCoin: \"0.42000000\"," +
                        "outgoingType: \"LTC\"," +
                        "transaction: " +
                        "\"66fa0b4c11227f9f05efa13d23e58c65b50acbd6395a126b5cd751064e6e79df\"" +
                        "}");
        ShapeShiftTxStatus txStatus = new ShapeShiftTxStatus(json);
        assertNotNull(txStatus);
        assertFalse(txStatus.isError);
        assertEquals(ShapeShiftTxStatus.Status.COMPLETE, txStatus.status);
        assertEquals("1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", txStatus.address.toString());
        assertEquals(BTC, txStatus.address.getType());
        assertEquals("LMmeBWH17TWkQKvK7YFio2oiimPAzrHG6f", txStatus.withdraw.toString());
        assertEquals(LTC, txStatus.withdraw.getType());
        assertEquals(BTC.value("0.00297537"), txStatus.incomingValue);
        assertEquals(LTC.value("0.42"), txStatus.outgoingValue);
        assertEquals("66fa0b4c11227f9f05efa13d23e58c65b50acbd6395a126b5cd751064e6e79df",
                txStatus.transactionId);
    }

    @Test
    public void testTxStatus3Alt() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{\n" +
                        "status: \"complete\",\n" +
                        "address: \"1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN\",\n" +
                        "withdraw: \"BB6kZZi87mCd7mC1tWWJjuKGPTYQ1n2Fcg\",\n" +
                        "incomingCoin: 0.01,\n" +
                        "incomingType: \"BTC\",\n" +
                        "outgoingCoin: \"2.32997513\",\n" +
                        "outgoingType: \"NBT\",\n" +
                        "transaction: \"66fa0b4c11227f9f05efa13d23e58c65b50acbd6395a126b5cd751064e6e79df\"\n" +
                        "}");
        ShapeShiftTxStatus txStatus = new ShapeShiftTxStatus(json);
        assertNotNull(txStatus);
        assertFalse(txStatus.isError);
        assertEquals(ShapeShiftTxStatus.Status.COMPLETE, txStatus.status);
        assertEquals("1NDQPAGamGePkSZXW2CYBzXJEefB7N4bTN", txStatus.address.toString());
        assertEquals(BTC, txStatus.address.getType());
        assertEquals("BB6kZZi87mCd7mC1tWWJjuKGPTYQ1n2Fcg", txStatus.withdraw.toString());
        assertEquals(NBT, txStatus.withdraw.getType());
        assertEquals(BTC.value("0.01"), txStatus.incomingValue);
        assertEquals(NBT.value("2.33"), txStatus.outgoingValue);
        assertEquals("66fa0b4c11227f9f05efa13d23e58c65b50acbd6395a126b5cd751064e6e79df",
                txStatus.transactionId);
    }

    @Test
    public void testTxStatus4() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "status: \"failed\"," +
                    "error: \"error\"" +
                "}");
        ShapeShiftTxStatus txStatus = new ShapeShiftTxStatus(json);
        assertNotNull(txStatus);
        assertTrue(txStatus.isError);
        assertEquals(ShapeShiftTxStatus.Status.FAILED, txStatus.status);
        assertEquals("error", txStatus.errorMessage);
        assertNull(txStatus.address);
        assertNull(txStatus.withdraw);
        assertNull(txStatus.incomingValue);
        assertNull(txStatus.outgoingValue);
        assertNull(txStatus.transactionId);
    }

    @Test
    public void testTxStatus5() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{status: \"new_fancy_optional_status\"}");
        ShapeShiftTxStatus txStatus = new ShapeShiftTxStatus(json);
        assertNotNull(txStatus);
        assertFalse(txStatus.isError);
        assertEquals(ShapeShiftTxStatus.Status.UNKNOWN, txStatus.status);
        assertNull(txStatus.address);
        assertNull(txStatus.withdraw);
        assertNull(txStatus.incomingValue);
        assertNull(txStatus.outgoingValue);
        assertNull(txStatus.transactionId);
    }

    @Test
    public void testNormalTx() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "\"deposit\":\"18ETaXCYhJ8sxurh41vpKC3E6Tu7oJ94q8\"," +
                    "\"depositType\":\"BTC\"," +
                    "\"withdrawal\":\"DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV\"," +
                    "\"withdrawalType\":\"DOGE\"" +
                "}");
        ShapeShiftNormalTx normalTx = new ShapeShiftNormalTx(json);
        assertNotNull(normalTx);
        assertFalse(normalTx.isError);
        assertEquals("btc_doge", normalTx.pair);
        assertEquals("18ETaXCYhJ8sxurh41vpKC3E6Tu7oJ94q8", normalTx.deposit.toString());
        assertEquals(BTC, normalTx.deposit.getType());
        assertEquals("DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV", normalTx.withdrawal.toString());
        assertEquals(DOGE, normalTx.withdrawal.getType());
    }

    @Test
    public void testAmountTxWithoutMinerFee() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "\"success\":{" +
                        "\"pair\":\"btc_doge\"," +
                        "\"withdrawal\":\"DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV\"," +
                        "\"withdrawalAmount\":\"1000\"," +
                        "\"deposit\":\"14gQ3xywKEUA6CfH61F8t2c6oB5nLnUjL5\"," +
                        "\"depositAmount\":\"0.00052327\"," +
                        "\"expiration\":1427149038191," +
                        "\"quotedRate\":\"1911057.69230769\"" +
                    "}" +
                "}");
        ShapeShiftAmountTx amountTx = new ShapeShiftAmountTx(json);
        assertNotNull(amountTx);
        assertFalse(amountTx.isError);
        assertEquals("btc_doge", amountTx.pair);
        assertEquals("14gQ3xywKEUA6CfH61F8t2c6oB5nLnUjL5", amountTx.deposit.toString());
        assertEquals(BTC, amountTx.deposit.getType());
        assertEquals(BTC.value("0.00052327"), amountTx.depositAmount);
        assertEquals("DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV", amountTx.withdrawal.toString());
        assertEquals(DOGE, amountTx.withdrawal.getType());
        assertEquals(DOGE.value("1000"), amountTx.withdrawalAmount);
        assertEquals(1427149038191L, amountTx.expiration.getTime());
        assertEquals(BTC.value("0.00052327"), amountTx.rate.convert(Value.parse(DOGE, "1000")));
    }

    @Test
    public void testAmountTxWithMinerFee() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "\"success\":{" +
                        "\"pair\":\"btc_doge\"," +
                        "\"withdrawal\":\"DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV\"," +
                        "\"withdrawalAmount\":\"1000\"," +
                        "\"minerFee\":\"1\"," +
                        "\"deposit\":\"14gQ3xywKEUA6CfH61F8t2c6oB5nLnUjL5\"," +
                        "\"depositAmount\":\"0.00052379\"," +
                        "\"expiration\":1427149038191," +
                        "\"quotedRate\":\"1911057.69230769\"" +
                    "}" +
                "}");
        ShapeShiftAmountTx amountTx = new ShapeShiftAmountTx(json);
        assertNotNull(amountTx);
        assertFalse(amountTx.isError);
        assertEquals("btc_doge", amountTx.pair);
        assertEquals("14gQ3xywKEUA6CfH61F8t2c6oB5nLnUjL5", amountTx.deposit.toString());
        assertEquals(BTC, amountTx.deposit.getType());
        assertEquals(BTC.value("0.00052379"), amountTx.depositAmount);
        assertEquals("DMHLQYG4j96V8cZX9WSuXxLs5RnZn6ibrV", amountTx.withdrawal.toString());
        assertEquals(DOGE, amountTx.withdrawal.getType());
        assertEquals(DOGE.value("1000"), amountTx.withdrawalAmount);
        assertEquals(1427149038191L, amountTx.expiration.getTime());
        assertEquals(BTC.value("0.00052379"), amountTx.rate.convert(Value.parse(DOGE, "1000")));
    }

    @Test
    public void testEmail() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject(
                "{" +
                    "\"email\":{" +
                        "\"status\":\"success\"," +
                        "\"message\":\"Email receipt sent\"" +
                    "}" +
                "}");
        ShapeShiftEmail email = new ShapeShiftEmail(json);
        assertNotNull(email);
        assertFalse(email.isError);
        assertEquals(ShapeShiftEmail.Status.SUCCESS, email.status);
        assertEquals("Email receipt sent", email.message);
    }

    @Test
    public void testCoinsError() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject("{ error: \"error\" }");
        ShapeShiftCoins coins = new ShapeShiftCoins(json);
        assertNotNull(coins);
        assertTrue(coins.isError);
        assertEquals("error", coins.errorMessage);
        assertNull(coins.coins);
    }

    @Test
    public void testCoinError() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject("{ error: \"error\" }");
        ShapeShiftCoin coin = new ShapeShiftCoin(json);
        assertNotNull(coin);
        assertTrue(coin.isError);
        assertEquals("error", coin.errorMessage);
        assertNull(coin.name);
        assertNull(coin.symbol);
        assertNull(coin.image);
        assertFalse(coin.isAvailable);
    }

    @Test
    public void testRateError() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject("{ error: \"error\" }");
        ShapeShiftRate rate = new ShapeShiftRate(json);
        assertNotNull(rate);
        assertTrue(rate.isError);
        assertEquals("error", rate.errorMessage);
        assertNull(rate.pair);
        assertNull(rate.rate);
    }

    @Test
    public void testLimitError() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject("{ error: \"error\" }");
        ShapeShiftLimit limit = new ShapeShiftLimit(json);
        assertNotNull(limit);
        assertTrue(limit.isError);
        assertEquals("error", limit.errorMessage);
        assertNull(limit.pair);
        assertNull(limit.limit);
        assertNull(limit.minimum);
    }

    @Test
    public void testTimeError() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject("{ error: \"error\" }");
        ShapeShiftTime time = new ShapeShiftTime(json);
        assertNotNull(time);
        assertTrue(time.isError);
        assertEquals("error", time.errorMessage);
        assertNull(time.status);
        assertEquals(-1, time.secondsRemaining);
    }

    @Test
    public void testTxStatusError() throws JSONException, ShapeShiftException {
        JSONObject json = new JSONObject("{ error: \"error\" }");
        ShapeShiftTxStatus txStatus = new ShapeShiftTxStatus(json);
        assertNotNull(txStatus);
        assertTrue(txStatus.isError);
        assertEquals("error", txStatus.errorMessage);
        assertNull(txStatus.status);
        assertNull(txStatus.address);
        assertNull(txStatus.withdraw);
        assertNull(txStatus.incomingValue);
        assertNull(txStatus.outgoingValue);
        assertNull(txStatus.transactionId);
    }

    @Test(expected = ShapeShiftException.class)
    public void testInvalidCoins() throws ShapeShiftException, JSONException {
        JSONObject json = new JSONObject(
                "{" +
                    "BTC: {" +
                        "name: \"Bitcoin\"," +
                        "symbol: \"BTC\"," +
                        "image: \"https://shapeshift.io/images/coins/bitcoin.png\"," +
                        "status: \"available\"" +
                    "}," +
                    "LTC: {" +
                        "bad: \"\"" +
                    "}" +
                "}");
        new ShapeShiftCoins(json);
    }

    @Test(expected = ShapeShiftException.class)
    public void testInvalidCoin() throws ShapeShiftException, JSONException {
        JSONObject json = new JSONObject(
                "{" +
                    "LTC: {" +
                        "bad: \"\"" +
                    "}" +
                "}");
        new ShapeShiftCoin(json);
    }

    @Test(expected = ShapeShiftException.class)
    public void testInvalidMarketInfo() throws JSONException, ShapeShiftException {
        new ShapeShiftRate(new JSONObject(
                "{" +
                        "\"pair\" : \"btc_nbt\"," +
                        "\"rate\" : \"0\"," +
                        "\"minerFee\" : \"0.01\"," +
                        "\"limit\" : \"0\"," +
                        "\"minimum\" : 0" +
                        "}"));
    }


    @Test(expected = ShapeShiftException.class)
    public void testInvalidRate() throws ShapeShiftException {
        new ShapeShiftRate(new JSONObject());
    }

    @Test(expected = ShapeShiftException.class)
    public void testInvalidLimit() throws ShapeShiftException {
        new ShapeShiftLimit(new JSONObject());
    }

    @Test(expected = ShapeShiftException.class)
    public void testInvalidTime() throws ShapeShiftException {
        new ShapeShiftTime(new JSONObject());
    }

    @Test(expected = ShapeShiftException.class)
    public void testInvalidTxStatus() throws ShapeShiftException {
        new ShapeShiftTime(new JSONObject());
    }
}