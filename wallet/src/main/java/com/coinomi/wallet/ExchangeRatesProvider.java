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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.util.Io;
import com.coinomi.wallet.util.WalletUtils;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import static com.coinomi.wallet.Constants.HTTP_TIMEOUT_MS;

/**
 * @author Andreas Schildbach
 */
public class ExchangeRatesProvider extends ContentProvider {


    public static class ExchangeRate {
        public ExchangeRate(@Nonnull final CoinType type,
                            @Nonnull final org.bitcoinj.utils.ExchangeRate rate,
                            final String source) {
            this.type = type;
            this.rate = rate;
            this.source = source;
        }

        public final CoinType type;
        public final org.bitcoinj.utils.ExchangeRate rate;
        public final String source;

        public String getCurrencyCode() {
            return rate.fiat.currencyCode;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '[' + rate.fiat + ']';
        }
    }

    private static final String DATABASE_TABLE = "exchange_rates";

    public static final String KEY_ROWID = "_id";
    public static final String KEY_CURRENCY_CODE = "currency_code";
    private static final String KEY_COIN_ID = "coin_id";
    private static final String KEY_RATE_COIN = "rate_coin";
    private static final String KEY_RATE_FIAT = "rate_fiat";
    private static final String KEY_SOURCE = "source";

    private static final String QUERY_PARAM_OFFLINE = "offline";

//    private Helper helper;
    private String userAgent;

    @CheckForNull
    private Map<CoinType, Map<String, ExchangeRate>> allExchangeRates =
            new HashMap<CoinType, Map<String, ExchangeRate>>(Constants.SUPPORTED_COINS.size());
    private Map<CoinType, Long> lastUpdatedRates =
            new HashMap<CoinType, Long>(Constants.SUPPORTED_COINS.size());

    private static final String COINOMI_BASE_URL = "https://ticker.coinomi.net/simple/crypto/%s";
    private static final String COINOMI_SOURCE = "coinomi.com";

    private static final Logger log = LoggerFactory.getLogger(ExchangeRatesProvider.class);

    @Override
    public boolean onCreate() {
        final Context context = getContext();

        this.userAgent = WalletApplication.httpUserAgent();
//        helper = new Helper(getContext());

//        final List<ExchangeRate> cachedExchangeRates = config.getCachedExchangeRates();
//        if (cachedExchangeRates != null) {
//            for (ExchangeRate cachedRate : cachedExchangeRates) {
//                Map<String, ExchangeRate> exchangeRates = new TreeMap<String, ExchangeRate>();
//                exchangeRates.put(cachedRate.getCurrencyCode(), cachedRate);
//                allExchangeRates.put(cachedRate.type, exchangeRates);
//            }
//        }

        return true;
    }

    public static Uri contentUri(@Nonnull final String packageName,
                                 @Nonnull final CoinType type,
                                 final boolean offline) {
        final Uri.Builder uri = Uri.parse("content://" + packageName + '.' + DATABASE_TABLE)
                .buildUpon().appendPath("crypto").appendPath(type.getSymbol());
        if (offline)
            uri.appendQueryParameter(QUERY_PARAM_OFFLINE, "1");
        return uri.build();
    }

    @Override
    public Cursor query(final Uri uri, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        final long now = System.currentTimeMillis();

        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DATABASE_TABLE);

        final List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() != 2) {
            throw new IllegalArgumentException("Unrecognized URI: " + uri);
        }

        final CoinType type = CoinID.typeFromSymbol(uri.getLastPathSegment());
        final boolean offline = uri.getQueryParameter(QUERY_PARAM_OFFLINE) != null;

        long lastUpdated = lastUpdatedRates.containsKey(type) ? lastUpdatedRates.get(type) : 0;

        if (!offline && (lastUpdated == 0 || now - lastUpdated > Constants.RATE_UPDATE_FREQ_MS)) {
            Map<String, ExchangeRate> newExchangeRates;

            URL url = null;
            try {
                url = new URL(String.format(COINOMI_BASE_URL, type.getSymbol()));
            } catch (final MalformedURLException x) {
                throw new RuntimeException(x); // Should not happen
            }

            newExchangeRates = requestExchangeRates(url, userAgent, COINOMI_SOURCE, type);

            if (newExchangeRates != null) {
                allExchangeRates.put(type, newExchangeRates);
                lastUpdatedRates.put(type, now);

//                final ExchangeRate exchangeRateToCache =
//                        bestExchangeRate(newExchangeRates, config.getExchangeCurrencyCode());
//                if (exchangeRateToCache != null)
//                    config.setCachedExchangeRate(exchangeRateToCache);
            }
        }

        Map<String, ExchangeRate> exchangeRates = allExchangeRates.get(type);

        if (exchangeRates == null)
            return null;

        final MatrixCursor cursor = new MatrixCursor(new String[]{BaseColumns._ID,
                KEY_CURRENCY_CODE, KEY_COIN_ID, KEY_RATE_COIN, KEY_RATE_FIAT, KEY_SOURCE});

        if (selection == null) {
            for (final Map.Entry<String, ExchangeRate> entry : exchangeRates.entrySet()) {
                final ExchangeRate exchangeRate = entry.getValue();
                final org.bitcoinj.utils.ExchangeRate rate = exchangeRate.rate;
                final String currencyCode = exchangeRate.getCurrencyCode();
                cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(type.getId())
                        .add(rate.coin.value).add(rate.fiat.value).add(exchangeRate.source);
            }
        } else if (selection.equals(KEY_CURRENCY_CODE)) {
            final String selectionArg = selectionArgs[0];
            final ExchangeRate exchangeRate = bestExchangeRate(exchangeRates, selectionArg);
            if (exchangeRate != null) {
                final org.bitcoinj.utils.ExchangeRate rate = exchangeRate.rate;
                final String currencyCode = exchangeRate.getCurrencyCode();
                cursor.newRow().add(currencyCode.hashCode()).add(currencyCode).add(type.getId())
                        .add(rate.coin.value).add(rate.fiat.value).add(exchangeRate.source);
            }
        }

        return cursor;
    }

    private ExchangeRate bestExchangeRate(Map<String, ExchangeRate> exchangeRates, final String currencyCode) {
        ExchangeRate rate = currencyCode != null ? exchangeRates.get(currencyCode) : null;
        if (rate != null)
            return rate;

        final String defaultCode = WalletUtils.localeCurrencyCode();
        rate = defaultCode != null ? exchangeRates.get(defaultCode) : null;

        if (rate != null)
            return rate;

        return exchangeRates.get(Constants.DEFAULT_EXCHANGE_CURRENCY);
    }

    public static ExchangeRate getExchangeRate(@Nonnull final Cursor cursor) {
        final String currencyCode = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_CODE));
        final String coinId = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_COIN_ID));
        final Coin rateCoin = Coin.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_COIN)));
        final Fiat rateFiat = Fiat.valueOf(currencyCode, cursor.getLong(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_RATE_FIAT)));
        final String source = cursor.getString(cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_SOURCE));

        return new ExchangeRate(CoinID.typeFromId(coinId),
                new org.bitcoinj.utils.ExchangeRate(rateCoin, rateFiat), source);
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


    // TODO do not make a request when no connection is available
    private static Map<String, ExchangeRate> requestExchangeRates(final URL url,
                                                                  final String userAgent,
                                                                  final String source,
                                                                  final CoinType type) {
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

                final Map<String, ExchangeRate> rates = new TreeMap<String, ExchangeRate>();

                final JSONObject head = new JSONObject(content.toString());
                for (final Iterator<String> i = head.keys(); i.hasNext(); ) {
                    final String currencyCode = i.next();
                    if (!"timestamp".equals(currencyCode)) {
                        final String rateStr = head.optString(currencyCode, null);
                        if (rateStr != null) {
                            try {
                                BigDecimal rateRaw = new BigDecimal(rateStr);
                                if (rateRaw.signum() > 0) {
                                    Coin rateCoin = type.getOneCoin();
                                    BigDecimal rateFiatUnit = rateRaw.movePointRight(Fiat.SMALLEST_UNIT_EXPONENT);

                                    // Scale the rate up because Fiat class uses only 4 decimal places
                                    // and precision is lost for low value coins
                                    while (rateFiatUnit.precision() != 1 && rateFiatUnit.longValue() < 10000) {
                                        // If the fiat rate is so small that
                                        rateFiatUnit = rateFiatUnit.multiply(BigDecimal.TEN);
                                        rateCoin = rateCoin.multiply(10);
                                    }

                                    final Fiat rateFiat = Fiat.valueOf(currencyCode, rateFiatUnit.longValue());

                                    rates.put(currencyCode, new ExchangeRate(type,
                                            new org.bitcoinj.utils.ExchangeRate(rateCoin, rateFiat),
                                            source));
                                }
                            } catch (final Exception x) {
                                log.warn("problem fetching {} exchange rate from {} ({}): {}", currencyCode, url, contentEncoding, x.getMessage());
                            }
                        }
                    }
                }

                log.info("fetched exchange rates from {} ({}), {} chars, took {} ms", url, contentEncoding, length, System.currentTimeMillis()
                        - start);

                return rates;
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

//    private static class Helper extends SQLiteOpenHelper {
//        private static final String DATABASE_NAME = "exchange_rates";
//        private static final int DATABASE_VERSION = 1;
//
//        private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " ("
//                + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
//                + KEY_CURRENCY_CODE + " TEXT NOT NULL, "
//                + KEY_COIN_ID + " TEXT NOT NULL, "
//                + KEY_RATE_COIN + " INTEGER NOT NULL, "
//                + KEY_RATE_FIAT + " INTEGER NOT NULL, "
//                + KEY_SOURCE + " TEXT NULL);";
//
//        public Helper(final Context context) {
//            super(context, DATABASE_NAME, null, DATABASE_VERSION);
//        }
//
//        @Override
//        public void onCreate(final SQLiteDatabase db) {
//            db.execSQL(DATABASE_CREATE);
//        }
//
//        @Override
//        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
//            db.beginTransaction();
//            try {
//                for (int v = oldVersion; v < newVersion; v++)
//                    upgrade(db, v);
//
//                db.setTransactionSuccessful();
//            } finally {
//                db.endTransaction();
//            }
//        }
//
//        private void upgrade(final SQLiteDatabase db, final int oldVersion) {
//            if (oldVersion == 1) {
//                // future
//            } else {
//                throw new UnsupportedOperationException("old=" + oldVersion);
//            }
//        }
//    }
}
