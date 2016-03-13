package com.coinomi.wallet.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.ui.widget.CoinListItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public class CoinExchangeListAdapter extends BaseAdapter {
    private final Context context;
    private final List<CoinType> coins;
    private final HashMap<String, ExchangeRate> rates;

    public CoinExchangeListAdapter(final Context context, List<CoinType> coins,
                                   @Nullable Map<String, ExchangeRate> rates) {
        this.context = context;
        this.coins = coins;
        this.rates = new HashMap<>(coins.size());
        setExchangeRates(rates);
    }

    public CoinExchangeListAdapter(final Context context, List<CoinType> coins) {
        this(context, coins, null);
    }

    @Override
    public int getCount() {
        return coins.size();
    }

    @Override
    public CoinType getItem(int position) {
        return coins.get(position);
    }

    public void setExchangeRates(@Nullable Map<String, ExchangeRate> newRates) {
        if (newRates != null) {
            for (ExchangeRate rate : newRates.values()) {
                if (isRateRelative(rate)) {
                    this.rates.put(rate.currencyCodeId, rate);
                }
            }
        } else {
            rates.clear();
        }
        notifyDataSetChanged();
    }

    private boolean isRateRelative(ExchangeRate rate) {
        if (rate.rate.value1.type instanceof CoinType && coins.contains(rate.rate.value1.type)) {
            return true;
        } else if (rate.rate.value2.type instanceof CoinType && coins.contains(rate.rate.value2.type)) {
            return true;
        }
        return false;
    }

    @Nullable
    public ExchangeRate getExchangeRate(String code) {
        return rates.get(code);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        if (row == null) {
            row = new CoinListItem(context);
        }

        CoinType coinType = getItem(position);
        if (coinType != null) {
            ((CoinListItem) row).setCoin(coinType);

            ExchangeRate rate = getExchangeRate(coinType.getSymbol());
            if (rate != null) ((CoinListItem) row).setExchangeRate(rate);
        }


        return row;
    }


}