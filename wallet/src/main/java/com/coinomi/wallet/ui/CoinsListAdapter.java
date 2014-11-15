package com.coinomi.wallet.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.CoinGridItem;

import javax.annotation.Nonnull;

/**
 * @author Giannis Dzegoutanis
 */
public class CoinsListAdapter extends BaseAdapter {
    private final Context context;
    private final WalletApplication application;

    public CoinsListAdapter(final Context context, @Nonnull final WalletApplication application) {
        this.context = context;
        this.application = application;
    }

    @Override
    public int getCount() {
//        if (application.getWallet() != null) {
//            return application.getWallet().getCoinTypes().size();
//        }
        return Constants.SUPPORTED_COINS.size();
    }

    @Override
    public CoinType getItem(int position) {
//        if (application.getWallet() != null) {
//            return application.getWallet().getCoinTypes().get(position);
//        }
        return Constants.SUPPORTED_COINS.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getUid();
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