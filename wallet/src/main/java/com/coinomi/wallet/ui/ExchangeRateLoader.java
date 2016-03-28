package com.coinomi.wallet.ui;

/*
 * Copyright 2012-2014 the original author or authors.
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

import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.ExchangeRatesProvider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.support.v4.content.CursorLoader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public final class ExchangeRateLoader extends CursorLoader implements OnSharedPreferenceChangeListener {
    private final Configuration config;
    private final String packageName;
    private final Context context;
    private String localCurrency;

    public ExchangeRateLoader(final Context context, final Configuration config,
                              final String localSymbol,
                              final String coinSymbol) {
        super(context, ExchangeRatesProvider.contentUriToCrypto(context.getPackageName(), localSymbol, false),
                null, ExchangeRatesProvider.KEY_CURRENCY_ID, new String[]{coinSymbol}, null);

        this.config = config;
        this.packageName = context.getPackageName();
        this.context = context;
        this.localCurrency = localSymbol;
    }

    public ExchangeRateLoader(final Context context, final Configuration config,
                              final String localSymbol) {
        super(context, ExchangeRatesProvider.contentUriToCrypto(context.getPackageName(), localSymbol, false),
                null, null, new String[]{null}, null);

        this.config = config;
        this.packageName = context.getPackageName();
        this.context = context;
        this.localCurrency = localSymbol;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        refreshUri(config.getExchangeCurrencyCode());

        config.registerOnSharedPreferenceChangeListener(this);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(Intent.ACTION_TIME_TICK);
        context.registerReceiver(broadcastReceiver, intentFilter);

        forceLoad();
    }

    @Override
    protected void onStopLoading() {
        config.unregisterOnSharedPreferenceChangeListener(this);
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (IllegalArgumentException e) { /* ignore */ }
        super.onStopLoading();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key))
            onCurrencyChange();
    }

    private void onCurrencyChange() {
        refreshUri(config.getExchangeCurrencyCode());
        forceLoad();
    }

    private void refreshUri(String newLocalCurrency) {
        if (!newLocalCurrency.equals(localCurrency)) {
            localCurrency = newLocalCurrency;
            setUri(ExchangeRatesProvider.contentUriToCrypto(packageName, localCurrency, false));
        }
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        private boolean hasConnectivity = true;

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                hasConnectivity = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
                if (hasConnectivity) {
                    forceLoad();
                }
            } else if (Intent.ACTION_TIME_TICK.equals(action) && hasConnectivity) {
                forceLoad();
            }
        }
    };
}
