package com.coinomi.core.exchange.shapeshift;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.squareup.okhttp.Cache;
import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.OkHttpClient;

import java.io.File;
import java.net.URL;
import java.util.Collections;

/**
 * @author John L. Jegutanis
 */
abstract public class Connection {
    private static final String DEFAULT_BASE_URL = "https://shapeshift.io/";

    OkHttpClient client;
    String baseUrl = DEFAULT_BASE_URL;

    protected Connection(OkHttpClient client) {
        this.client = client;
    }

    protected Connection() {
        client = new OkHttpClient();
        client.setConnectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS));
    }

    /**
     * Setup caching. The cache directory should be private, and untrusted applications should not
     * be able to read its contents!
     */
    public void setCache(File cacheDirectory) {
        int cacheSize = 256 * 1024; // 256 KiB
        Cache cache = new Cache(cacheDirectory, cacheSize);
        client.setCache(cache);
    }

    public boolean isCacheSet() {
        return client.getCache() != null;
    }

    protected String getApiUrl(String path) {
        return baseUrl + path;
    }
}
