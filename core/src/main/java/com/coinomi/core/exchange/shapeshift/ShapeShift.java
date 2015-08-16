package com.coinomi.core.exchange.shapeshift;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftAmountTx;
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
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class ShapeShift extends Connection {
    private static final Logger log = LoggerFactory.getLogger(ShapeShift.class);

    private static final MediaType MEDIA_TYPE_JSON
            = MediaType.parse("application/json");

    private static final String GET_COINS_API = "getcoins";
    private static final String MARKET_INFO_API = "marketinfo/%s";
    private static final String RATE_API = "rate/%s";
    private static final String LIMIT_API = "limit/%s";
    private static final String TIME_REMAINING_API = "timeremaining/%s";
    private static final String TX_STATUS_API = "txStat/%s";
    private static final String NORMAL_TX_API = "shift";
    private static final String FIXED_AMOUNT_TX_API = "sendamount";
    private static final String EMAIL_RECEIPT_API = "mail";

    private String apiPublicKey;

    public ShapeShift(OkHttpClient client) {
        super(client);
    }

    public ShapeShift() {}

    public void setApiPublicKey(String apiPublicKey) {
        this.apiPublicKey = apiPublicKey;
    }

    /**
     * Get List of Supported Coins
     *
     * List of all the currencies that Shapeshift currently supports at any given time. Sometimes
     * coins become temporarily unavailable during updates or unexpected service issues.
     */
    public ShapeShiftCoins getCoins() throws ShapeShiftException, IOException {
        Request request = new Request.Builder().url(getApiUrl(GET_COINS_API)).build();
        return new ShapeShiftCoins(getMakeApiCall(request));
    }

    /**
     * Get Market Info
     *
     * This is a combined call for {@link #getRate(CoinType, CoinType) getRate()} and
     * {@link #getLimit(CoinType, CoinType) getLimit()} API calls.
     */
    public ShapeShiftMarketInfo getMarketInfo(CoinType typeFrom, CoinType typeTo)
            throws ShapeShiftException, IOException {
        return getMarketInfo(getPair(typeFrom, typeTo));
    }

    /**
     * Get Market Info
     *
     * This is a combined call for {@link #getRate(CoinType, CoinType) getRate()} and
     * {@link #getLimit(CoinType, CoinType) getLimit()} API calls.
     */
    public ShapeShiftMarketInfo getMarketInfo(String pair)
            throws ShapeShiftException, IOException {
        log.info("Market info for pair {}", pair);
        String apiUrl = getApiUrl(String.format(MARKET_INFO_API, pair));
        Request request = new Request.Builder().url(apiUrl).build();
        ShapeShiftMarketInfo reply = new ShapeShiftMarketInfo(getMakeApiCall(request));
        if (!reply.isError) checkPair(pair, reply.pair);
        return reply;
    }

    /**
     * Rate
     *
     * Gets the current rate offered by Shapeshift. This is an estimate because the rate can
     * occasionally change rapidly depending on the markets. The rate is also a 'use-able' rate not
     * a direct market rate. Meaning multiplying your input coin amount times the rate should give
     * you a close approximation of what will be sent out. This rate does not include the
     * transaction (miner) fee taken off every transaction.
     */
    public ShapeShiftRate getRate(CoinType typeFrom, CoinType typeTo)
            throws ShapeShiftException, IOException {
        String pair = getPair(typeFrom, typeTo);
        String apiUrl = getApiUrl(String.format(RATE_API, pair));
        Request request = new Request.Builder().url(apiUrl).build();
        ShapeShiftRate reply = new ShapeShiftRate(getMakeApiCall(request));
        if (!reply.isError) checkPair(pair, reply.pair);
        return reply;
    }

    /**
     * Deposit Limit
     *
     * Gets the current deposit limit set by Shapeshift. Amounts deposited over this limit will be
     * sent to the return address if one was entered, otherwise the user will need to contact
     * ShapeShift support to retrieve their coins. This is an estimate because a sudden market swing
     * could move the limit.
     */
    public ShapeShiftLimit getLimit(CoinType typeFrom, CoinType typeTo)
            throws ShapeShiftException, IOException {
        String pair = getPair(typeFrom, typeTo);
        String apiUrl = getApiUrl(String.format(LIMIT_API, pair));
        Request request = new Request.Builder().url(apiUrl).build();
        ShapeShiftLimit reply = new ShapeShiftLimit(getMakeApiCall(request));
        if (!reply.isError) checkPair(pair, reply.pair);
        return reply;
    }

    /**
     * Time Remaining on Fixed Amount Transaction
     *
     * When a transaction is created with a fixed amount requested there is a 10 minute window for
     * the deposit. After the 10 minute window if the deposit has not been received the transaction
     * expires and a new one must be created. This api call returns how many seconds are left before
     * the transaction expires.
     */
    public ShapeShiftTime getTime(AbstractAddress address) throws ShapeShiftException, IOException {
        String apiUrl = getApiUrl(String.format(TIME_REMAINING_API, address.toString()));
        Request request = new Request.Builder().url(apiUrl).build();
        return new ShapeShiftTime(getMakeApiCall(request));
    }

    /**
     * Status of deposit to address
     *
     * This returns the status of the most recent deposit transaction to the address.
     */
    public ShapeShiftTxStatus getTxStatus(AbstractAddress address)
            throws ShapeShiftException, IOException {
        String apiUrl = getApiUrl(String.format(TX_STATUS_API, address.toString()));
        Request request = new Request.Builder().url(apiUrl).build();
        ShapeShiftTxStatus reply = new ShapeShiftTxStatus(getMakeApiCall(request));
        if (!reply.isError && reply.address != null) checkAddress(address, reply.address);
        return new ShapeShiftTxStatus(reply, address);
    }

    /**
     * Normal Transaction
     *
     * Make a normal exchange and receive with {@code withdrawal} address. The exchange pair is
     * determined from the {@link CoinType}s of {@code refund} and {@code withdrawal}.
     */
    public ShapeShiftNormalTx exchange(AbstractAddress withdrawal, AbstractAddress refund)
            throws ShapeShiftException, IOException {

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("withdrawal", withdrawal.toString());
            requestJson.put("pair", getPair(refund.getType(), withdrawal.getType()));
            requestJson.put("returnAddress", refund.toString());
            if (apiPublicKey != null) requestJson.put("apiKey", apiPublicKey);
        } catch (JSONException e) {
            throw new ShapeShiftException("Could not create a JSON request", e);
        }

        String apiUrl = getApiUrl(NORMAL_TX_API);
        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, requestJson.toString());
        Request request = new Request.Builder().url(apiUrl).post(body).build();
        ShapeShiftNormalTx reply = new ShapeShiftNormalTx(getMakeApiCall(request));
        if (!reply.isError) checkAddress(withdrawal, reply.withdrawal);
        return reply;
    }


    /**
     * Fixed Amount Transaction
     *
     * This call allows you to request a fixed amount to be sent to the {@code withdrawal} address.
     * You provide a withdrawal address and the amount you want sent to it. We return the amount
     * to deposit and the address to deposit to. This allows you to use shapeshift as a payment
     * mechanism.
     *
     * The exchange pair is determined from the {@link CoinType}s of {@code refund} and
     * {@code withdrawal}.
     */
    public ShapeShiftAmountTx exchangeForAmount(Value amount, AbstractAddress withdrawal,
                                                AbstractAddress refund)
            throws ShapeShiftException, IOException {
        String pair = getPair(refund.getType(), withdrawal.getType());
        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("withdrawal", withdrawal.toString());
            requestJson.put("pair", pair);
            requestJson.put("returnAddress", refund.toString());
            requestJson.put("amount", amount.toPlainString());
            if (apiPublicKey != null) requestJson.put("apiKey", apiPublicKey);
        } catch (JSONException e) {
            throw new ShapeShiftException("Could not create a JSON request", e);
        }

        String apiUrl = getApiUrl(FIXED_AMOUNT_TX_API);
        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, requestJson.toString());
        Request request = new Request.Builder().url(apiUrl).post(body).build();
        ShapeShiftAmountTx reply = new ShapeShiftAmountTx(getMakeApiCall(request));
        if (!reply.isError) {
            checkPair(pair, reply.pair);
            checkValue(amount, reply.withdrawalAmount);
            checkAddress(withdrawal, reply.withdrawal);
        }
        return reply;
    }

    /**
     * Request email receipt
     *
     * This call allows you to request a fixed amount to be sent to the {@code withdrawal} address.
     * You provide a withdrawal address and the amount you want sent to it. We return the amount
     * to deposit and the address to deposit to. This allows you to use shapeshift as a payment
     * mechanism.
     *
     * The exchange pair is determined from the {@link CoinType}s of {@code refund} and
     * {@code withdrawal}.
     */
    public ShapeShiftEmail requestEmailReceipt(String email, ShapeShiftTxStatus txStatus)
            throws ShapeShiftException, IOException {

        JSONObject requestJson = new JSONObject();
        try {
            requestJson.put("email", email);
            checkState(txStatus.status == ShapeShiftTxStatus.Status.COMPLETE,
                    "Transaction not complete");
            requestJson.put("txid", checkNotNull(txStatus.transactionId, "Null transaction id"));
        } catch (Exception e) {
            throw new ShapeShiftException("Could not create a JSON request", e);
        }

        String apiUrl = getApiUrl(EMAIL_RECEIPT_API);
        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, requestJson.toString());
        Request request = new Request.Builder().url(apiUrl).post(body).build();
        return new ShapeShiftEmail(getMakeApiCall(request));
    }


    /**
     * Convert types to the ShapeShift format. For example Bitcoin to Litecoin will become btc_ltc.
     */
    public static String getPair(CoinType typeFrom, CoinType typeTo) {
        return typeFrom.getSymbol().toLowerCase() + "_" + typeTo.getSymbol().toLowerCase();
    }

    private void checkPair(String expectedPair, String pair)
            throws ShapeShiftException {
        if (!expectedPair.equals(pair)) {
            String errorMsg = String.format("Pair mismatch, expected %s but got %s.",
                    expectedPair, pair);
            throw new ShapeShiftException(errorMsg);
        }
    }

    private void checkValue(Value expected, Value value) throws ShapeShiftException {
        if (!expected.equals(value)) {
            String errorMsg = String.format("Value mismatch, expected %s but got %s.",
                    expected, value);
            throw new ShapeShiftException(errorMsg);
        }
    }

    private void checkAddress(AbstractAddress expected, AbstractAddress address) throws ShapeShiftException {
        if (!expected.getType().equals(address.getType()) ||
            !expected.toString().equals(address.toString())) {
            String errorMsg = String.format("Address mismatch, expected %s but got %s.",
                    expected, address);
            throw new ShapeShiftException(errorMsg);
        }
    }

    private JSONObject getMakeApiCall(Request request) throws ShapeShiftException, IOException {
        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                JSONObject reply = parseReply(response);
                String genericMessage = "Error code " + response.code();
                throw new IOException(
                        reply != null ? reply.optString("error", genericMessage) : genericMessage);
            }
            return parseReply(response);
        } catch (JSONException e) {
            throw new ShapeShiftException("Could not parse JSON", e);
        }
    }

    private static JSONObject parseReply(Response response) throws IOException, JSONException {
        return new JSONObject(response.body().string());
    }

    public static CoinType[] parsePair(String pair) {
        String[] pairs = pair.split("_");
        checkState(pairs.length == 2);
        return new CoinType[]{CoinID.typeFromSymbol(pairs[0]), CoinID.typeFromSymbol(pairs[1])};
    }
}
