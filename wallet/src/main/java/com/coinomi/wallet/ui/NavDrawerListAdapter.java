package com.coinomi.wallet.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.NavDrawerItem;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

/**
 * @author John L. Jegutanis
 */
public class NavDrawerListAdapter extends BaseAdapter {
    private final Context context;
    private final WalletApplication application;
    private List<WalletAccount> items;

    public NavDrawerListAdapter(final Context context, @Nonnull final WalletApplication application) {
        this.context = context;
        this.application = application;
        buildData();
    }

    @Override
    public void notifyDataSetChanged() {
        buildData();
        super.notifyDataSetChanged();
    }

    private void buildData() {
        if (application.getWallet() != null) {
            items = application.getWallet().getAllAccounts();
        } else {
            items = new ArrayList<WalletAccount>();
        }
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public WalletAccount getItem(int position) {
        if (items.size() > position) {
            return items.get(position);
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        if (row == null) {
            row = new NavDrawerItem(context);
        }

        WalletAccount account = getItem(position);
        if (account != null) ((NavDrawerItem) row).setAccount(account);

        return row;
    }
}