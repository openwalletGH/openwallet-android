package com.coinomi.wallet;

/*
 * Copyright 2011-2014 the original author or authors.
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
 */


import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.FiatType;
import com.coinomi.core.coins.FiatValue;
import com.coinomi.core.coins.Value;
import com.coinomi.wallet.util.Io;
import com.google.common.base.Charsets;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.coinomi.wallet.Constants.HTTP_TIMEOUT_MS;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesProvider extends ContentProvider {

    public static class ExchangeRate {
        @Nonnull public final com.coinomi.core.util.ExchangeRate rate;
        public final String currencyCodeId;
        @Nullable public final String source;

        public ExchangeRate(@Nonnull final com.coinomi.core.util.ExchangeRate rate,
                            final String currencyCodeId, @Nullable final String source) {
            this.rate = rate;
            this.currencyCodeId = currencyCodeId;
            this.source = source;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '[' + rate.value1 + " ~ " + rate.value2 + ']';
        }
    }

    public static final String KEY_CURRENCY_ID = "currency_id";
    private static final String KEY_RATE_COIN = "rate_coin";
    private static final String KEY_RATE_FIAT = "rate_fiat";
    private static final String KEY_RATE_COIN_CODE = "rate_coin_code";
    private static final String KEY_RATE_FIAT_CODE = "rate_fiat_code";
    private static final String KEY_SOURCE = "source";

    private static final String QUERY_PARAM_OFFLINE = "offline";

    private ConnectivityManager connManager;
    private Configuration config;
    private String userAgent;

    private Map<String, ExchangeRate> localToCryptoRates = null;
    private long localToCryptoLastUpdated = 0;
    private String lastLocalCurrency = null;

    private Map<String, ExchangeRate> cryptoToLocalRates = null;
    private long cryptoToLocalLastUpdated = 0;
    private String lastCryptoCurrency = null;

    private static final String BASE_URL = "https://ticker.coinomi.net/simple";
    private static final String TO_LOCAL_URL = BASE_URL + "/to-local/%s";
    private static final String TO_CRYPTO_URL = BASE_URL + "/to-crypto/%s";
    private static final String COINOMI_SOURCE = "coinomi.com";

    private static final Logger log = LoggerFactory.getLogger(ExchangeRatesProvider.class);

    @Override
    public boolean onCreate() {
        final Context context = getContext();

        connManager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        config = new Configuration(PreferenceManager.getDefaultSharedPreferences(context));
        userAgent = WalletApplication.httpUserAgent();

        lastLocalCurrency = config.getCachedExchangeLocalCurrency();
        if (lastLocalCurrency != null) {
            localToCryptoRates = parseExchangeRates(
                    config.getCachedExchangeRatesJson(), lastLocalCurrency, true);
            localToCryptoLastUpdated = 0;
        }

        return true;
    }

    private static Uri.Builder contentUri(@Nonnull final String packageName, final boolean offline) {
        final Uri.Builder builder =
                Uri.parse("content://" + packageName + ".exchange_rates").buildUpon();
        if (offline)
            builder.appendQueryParameter(QUERY_PARAM_OFFLINE, "1");
        return builder;
    }

    public static Uri contentUriToLocal(@Nonnull final String packageName,
                                  @Nonnull final String coinSymbol,
                                  final boolean offline) {
        final Uri.Builder uri = contentUri(packageName, offline);
        uri.appendPath("to-local").appendPath(coinSymbol);
        return uri.build();
    }

    public static Uri contentUriToCrypto(@Nonnull final String packageName,
                                  @Nonnull final String localSymbol,
                                  final boolean offline) {
        final Uri.Builder uri = contentUri(packageName, offline);
        uri.appendPath("to-crypto").appendPath(localSymbol);
        return uri.build();
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        final long now = System.currentTimeMillis();

        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 2) {
            throw new IllegalArgumentException("Unrecognized URI: " + uri);
        }

        final boolean offline = uri.getQueryParameter(QUERY_PARAM_OFFLINE) != null;
        long lastUpdated;

        final String symbol;
        final boolean isLocalToCrypto;

        if (pathSegments.get(0).equals("to-crypto")) {
            isLocalToCrypto = true;
            symbol = pathSegments.get(1);
            lastUpdated = symbol.equals(lastLocalCurrency) ? localToCryptoLastUpdated : 0;
        } else if (pathSegments.get(0).equals("to-local")) {
            isLocalToCrypto = false;
            symbol = pathSegments.get(1);
            lastUpdated = symbol.equals(lastCryptoCurrency) ? cryptoToLocalLastUpdated : 0;
        } else {
            throw new IllegalArgumentException("Unrecognized URI path: " + uri);
        }

        if (!offline && (lastUpdated == 0 || now - lastUpdated > Constants.RATE_UPDATE_FREQ_MS)) {
            URL url;
            try {
                if (isLocalToCrypto) {
                    url = new URL(String.format(TO_CRYPTO_URL, symbol));
                } else {
                    url = new URL(String.format(TO_LOCAL_URL, symbol));
                }
            } catch (final MalformedURLException x) {
                throw new RuntimeException(x); // Should not happen
            }

            JSONObject newExchangeRatesJson = requestExchangeRatesJson(url, userAgent);
            Map<String, ExchangeRate> newExchangeRates =
                    parseExchangeRates(newExchangeRatesJson, symbol, isLocalToCrypto);

            if (newExchangeRates != null) {
                if (isLocalToCrypto) {
                    localToCryptoRates = newExchangeRates;
                    localToCryptoLastUpdated = now;
                    lastLocalCurrency = symbol;
                    config.setCachedExchangeRates(lastLocalCurrency, newExchangeRatesJson);
                } else {
                    cryptoToLocalRates = newExchangeRates;
                    cryptoToLocalLastUpdated = now;
                    lastCryptoCurrency = symbol;
                }
            }
        }

        Map<String, ExchangeRate> exchangeRates = isLocalToCrypto ? localToCryptoRates : cryptoToLocalRates;

        if (exchangeRates == null)
            return null;

        final MatrixCursor cursor = new MatrixCursor(new String[]{BaseColumns._ID,
                KEY_CURRENCY_ID, KEY_RATE_COIN, KEY_RATE_COIN_CODE, KEY_RATE_FIAT, KEY_RATE_FIAT_CODE, KEY_SOURCE});

        if (selection == null) {
            for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet()) {
                final ExchangeRate exchangeRate = entry.getValue();
                addRow(cursor, exchangeRate);
            }
        } else if (selection.equals(KEY_CURRENCY_ID)) {
            final ExchangeRate exchangeRate = exchangeRates.get(selectionArgs[0]);
            if (exchangeRate != null) {
                addRow(cursor, exchangeRate);
            }
        }

        return cursor;
    }

    private void addRow(MatrixCursor cursor, ExchangeRate exchangeRate) {
        final com.coinomi.core.util.ExchangeRate rate = exchangeRate.rate;
        final String codeId = exchangeRate.currencyCodeId;
        cursor.newRow().add(codeId.hashCode()).add(codeId)
                .add(rate.value1.value).add(rate.value1.type.getSymbol())
                .add(rate.value2.value).add(rate.value2.type.getSymbol())
                .add(exchangeRate.source);
    }

    public static String getCurrencyCodeId(@Nonnull final Cursor cursor) {
        return cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_ID));
    }

    public static ExchangeRate getExchangeRate(@Nonnull final Cursor cursor) {
        final String codeId = getCurrencyCodeId(cursor);
        final CoinType type = CoinID.typeFromSymbol(cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_COIN_CODE)));
        final Value rateCoin = Value.valueOf(type, cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_COIN)));
        final String fiatCode = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_FIAT_CODE));
        final Value rateFiat = FiatValue.valueOf(fiatCode, cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_FIAT)));
        final String source = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_SOURCE));

        com.coinomi.core.util.ExchangeRate rate = new com.coinomi.core.util.ExchangeRate(rateCoin, rateFiat);
        return new ExchangeRate(rate, codeId, source);
    }

    @Override
    public Uri insert(final Uri uri, final ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(final Uri uri, final String selection, final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(final Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    private JSONObject requestExchangeRatesJson(final URL url, final String userAgent) {
        // Return null if no connection
        final NetworkInfo activeInfo = connManager.getActiveNetworkInfo();
        if (activeInfo == null || !activeInfo.isConnected()) return null;

        final long start = System.currentTimeMillis();

        HttpURLConnection connection = null;
        Reader reader = null;

        try {
            connection = (HttpURLConnection) url.openConnection();

            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);
            connection.addRequestProperty("User-Agent", userAgent);
            connection.addRequestProperty("Accept-Encoding", "gzip");
            connection.connect();

            final int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                final String contentEncoding = connection.getContentEncoding();

                InputStream is = new BufferedInputStream(connection.getInputStream(), 1024);
                if ("gzip".equalsIgnoreCase(contentEncoding))
                    is = new GZIPInputStream(is);

                reader = new InputStreamReader(is, Charsets.UTF_8);
                final StringBuilder content = new StringBuilder();
                final long length = Io.copy(reader, content);

                log.info("fetched exchange rates from {} ({}), {} chars, took {} ms", url,
                        contentEncoding, length, System.currentTimeMillis() - start);

                return new JSONObject(content.toString());
            } else {
                log.warn("http status {} when fetching exchange rates from {}", responseCode, url);
            }
        } catch (final Exception x) {
            log.warn("problem fetching exchange rates from " + url, x);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException x) {
                    // swallow
                }
            }

            if (connection != null)
                connection.disconnect();
        }

        return null;
    }

    private Map<String, ExchangeRate> parseExchangeRates(JSONObject json, String fromSymbol, boolean isLocalToCrypto) {
        if (json == null) return null;

        final Map<String, ExchangeRate> rates = new TreeMap<String, ExchangeRate>();
        try {
            CoinType type = isLocalToCrypto ? null : CoinID.typeFromSymbol(fromSymbol);
            for (final Iterator<String> i = json.keys(); i.hasNext(); ) {
                final String toSymbol = i.next();
                // Skip extras field
                if (!"extras".equals(toSymbol)) {
                    final String rateStr = json.optString(toSymbol, null);
                    if (rateStr != null) {
                        try {
                            if (isLocalToCrypto) type = CoinID.typeFromSymbol(toSymbol);
                            String localSymbol = isLocalToCrypto ? fromSymbol : toSymbol;
                            final Value rateCoin = type.oneCoin();
                            final Value rateLocal = FiatValue.parse(localSymbol, rateStr);

                            com.coinomi.core.util.ExchangeRate rate = new com.coinomi.core.util.ExchangeRate(rateCoin, rateLocal);
                            rates.put(toSymbol, new ExchangeRate(rate, toSymbol, COINOMI_SOURCE));
                        } catch (final Exception x) {
                            log.debug("ignoring {}/{}: {}", toSymbol, fromSymbol, x.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("problem parsing exchange rates: {}", e.getMessage());
        }

        if (rates.size() == 0) {
            return null;
        } else {
            return rates;
        }
    }
}
