package com.coinomi.wallet.util;

import android.content.Context;

import com.coinomi.wallet.Constants;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author John L. Jegutanis
 */
public class NetworkUtils {
    private static OkHttpClient httpClient;

    public static OkHttpClient getHttpClient(Context context) {
        if (httpClient == null) {
            httpClient = new OkHttpClient();
            httpClient.setConnectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
            httpClient.setConnectTimeout(Constants.NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            // Setup cache
            File cacheDir = new File(context.getCacheDir(), Constants.HTTP_CACHE_NAME);
            Cache cache = new Cache(cacheDir, Constants.HTTP_CACHE_SIZE);
            httpClient.setCache(cache);
        }
        return httpClient;
    }
}
