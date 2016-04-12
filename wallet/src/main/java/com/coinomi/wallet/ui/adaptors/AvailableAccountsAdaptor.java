package com.coinomi.wallet.ui.adaptors;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletPocketHD;
import com.coinomi.wallet.ui.widget.NavDrawerItemView;
import com.coinomi.wallet.util.WalletUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author John L. Jegutanis
 */
public class AvailableAccountsAdaptor extends BaseAdapter {

    private final Context context;
    private List<Entry> entries;

    public static class Entry {
        final public int iconRes;
        final public String title;
        final public Object accountOrCoinType;

        public Entry(WalletAccount account) {
            iconRes = WalletUtils.getIconRes(account);
            title = account.getDescriptionOrCoinName();
            accountOrCoinType = account;
        }

        public Entry(CoinType type) {
            iconRes = WalletUtils.getIconRes(type);
            title = type.getName();
            accountOrCoinType = type;
        }

        // Used for search
        private Entry(Object accountOrCoinType) {
            iconRes = -1;
            title = null;
            this.accountOrCoinType = accountOrCoinType;
        }

        @Override
        public boolean equals(Object o) {
//            return accountOrCoinType.getClass().isInstance(o) && accountOrCoinType.equals(o);
            boolean result = accountOrCoinType.getClass().isInstance(o);
            result = result && accountOrCoinType.equals(o);
            return result;
        }

        public CoinType getType() {
            if (accountOrCoinType instanceof CoinType) {
                return (CoinType) accountOrCoinType;
            } else if (accountOrCoinType instanceof WalletAccount) {
                return ((WalletAccount) accountOrCoinType).getCoinType();
            } else {
                throw new IllegalStateException("No cointype available");
            }
        }
    }

    public AvailableAccountsAdaptor(final Context context) {
        this.context = context;
        entries = ImmutableList.of();
    }

    /**
     * Create an adaptor that contains all accounts that are in the validTypes list.
     *
     * If includeTypes is true, it will also include any coin type that is in not in accounts but is
     * in the validTypes.
     */
    public AvailableAccountsAdaptor(final Context context, final List<WalletAccount> accounts,
                                    final List<CoinType> validTypes, final boolean includeTypes) {
        this.context = context;
        entries = createEntries(accounts, validTypes, includeTypes);
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    public int getAccountOrTypePosition(Object accountOrCoinType) {
        return entries.indexOf(accountOrCoinType);
    }

    /**
     * Update the adaptor to include all accounts that are in the validTypes list.
     *
     * If includeTypes is true, it will also include any coin type that is in not in accounts but is
     * in the validTypes.
     */
    public void update(final List<WalletAccount> accounts, final List<CoinType> validTypes,
                       final boolean includeTypes) {
        entries = createEntries(accounts, validTypes, includeTypes);
        notifyDataSetChanged();
    }

    private static List<Entry> createEntries(final List<WalletAccount> accounts,
                                                      final List<CoinType> validTypes,
                                                      final boolean includeTypes) {
        final ArrayList<CoinType> typesToAdd = Lists.newArrayList(validTypes);

        final ImmutableList.Builder<Entry> listBuilder = ImmutableList.builder();
        for (WalletAccount account : accounts) {
            if (validTypes.contains(account.getCoinType())) {
                listBuilder.add(new Entry(account));
                // Don't add this type as we just added the account for this type
                typesToAdd.remove(account.getCoinType());
            }
        }

        if (includeTypes) {
            for (CoinType type : typesToAdd) {
                listBuilder.add(new Entry(type));
            }
        }

        return listBuilder.build();
    }

    public List<CoinType> getTypes() {
        Set<CoinType> types = new HashSet<>();
        for (AvailableAccountsAdaptor.Entry entry : entries) {
            types.add(entry.getType());
        }
        return ImmutableList.copyOf(types);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public Entry getItem(int position) {
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

        final Entry entry = getItem(position);
        ((NavDrawerItemView) convertView).setData(entry.title, entry.iconRes);

        return convertView;
    }
}
