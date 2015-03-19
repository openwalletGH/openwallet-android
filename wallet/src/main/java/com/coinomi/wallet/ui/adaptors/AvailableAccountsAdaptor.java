package com.coinomi.wallet.ui.adaptors;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.ui.widget.NavDrawerItemView;
import com.coinomi.wallet.util.WalletUtils;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author John L. Jegutanis
 */
public class AvailableAccountsAdaptor extends BaseAdapter {

    private final Context context;
    private final List<Entry> entries;

    private static class Entry {
        public int iconRes;
        public String title;

        public Entry(WalletAccount account) {
            iconRes = WalletUtils.getIconRes(account);
            title = WalletUtils.getDescriptionOrCoinName(account);
        }

        public Entry(CoinType type) {
            iconRes = WalletUtils.getIconRes(type);
            title = type.getName();
        }
    }

    public AvailableAccountsAdaptor(final Context context, final List accountsOrCoinTypes) {
        this.context = context;
        this.entries = createEntries(accountsOrCoinTypes);
    }

    private static List<Entry> createEntries(List list) {
        ImmutableList.Builder<Entry> builder = ImmutableList.builder();
        for (Object o : list) {
            if (o instanceof CoinType) {
                builder.add(new Entry((CoinType) o));
            } else if (o instanceof WalletAccount) {
                builder.add(new Entry((WalletAccount) o));
            }
        }
        return builder.build();
    }


    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public Object getItem(int position) {
        return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = new NavDrawerItemView(context);
        }

        Entry entry = (Entry) getItem(position);
        ((NavDrawerItemView) convertView).setData(entry.title, entry.iconRes);

        return convertView;
    }
}
