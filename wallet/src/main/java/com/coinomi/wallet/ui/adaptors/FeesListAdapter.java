package com.coinomi.wallet.ui.adaptors;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.ui.widget.CoinListItem;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John L. Jegutanis
 */
public class FeesListAdapter extends BaseAdapter {
    private final Context context;
    private final Configuration config;
    private List<Value> fees;

    public FeesListAdapter(final Context context, Configuration config) {
        this.context = context;
        this.config = config;
        this.fees = new ArrayList<>();
        update();
    }

    public void update() {
        fees.clear();
        fees.addAll(config.getFeeValues().values());
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return fees.size();
    }

    @Override
    public Value getItem(int position) {
        return fees.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        if (view == null) {
            view = new CoinListItem(context);
        }

        CoinListItem row = (CoinListItem) view;

        Value fee = getItem(position);
        CoinType type = (CoinType) fee.type;
        row.setCoin(type);
        row.setAmount(fee);
        return row;
    }
}