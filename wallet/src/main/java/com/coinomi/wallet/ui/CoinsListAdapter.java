package com.coinomi.wallet.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.CoinGridItem;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * @author Giannis Dzegoutanis
 */
public class CoinsListAdapter extends BaseAdapter {
    private final Context context;
    private final List<CoinType> coins;

    public CoinsListAdapter(final Context context, List<CoinType> coins) {
        this.context = context;
        this.coins = coins;
    }

    @Override
    public int getCount() {
        return coins.size();
    }

    @Override
    public CoinType getItem(int position) {
        return coins.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        if (row == null) {
            row = new CoinGridItem(context);
        }

        CoinType coinType = getItem(position);
        if (coinType != null) ((CoinGridItem) row).setCoin(coinType);

        return row;
    }


}