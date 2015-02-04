package com.coinomi.wallet.ui;

/*
 * Copyright 2011-2014 the original author or authors.
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


import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.WalletUtils;

import org.bitcoinj.core.Address;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public class AddressesListAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final Resources res;

    private final WalletPocket pocket;
    private final List<Address> addresses = new ArrayList<Address>();
    private final Set<Address> usedAddresses = new HashSet<Address>();

    public AddressesListAdapter(final Context context, @Nonnull final WalletPocket walletPocket) {
        this.context = context;
        inflater = LayoutInflater.from(context);
        res = context.getResources();

        pocket = walletPocket;
    }

    public void clear() {
        addresses.clear();
        usedAddresses.clear();

        notifyDataSetChanged();
    }

    public void replace(@Nonnull final Collection<Address> addresses, Set<Address> usedAddresses) {
        this.addresses.clear();
        this.addresses.addAll(addresses);
        this.usedAddresses.clear();
        this.usedAddresses.addAll(usedAddresses);

        notifyDataSetChanged();
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public int getCount() {
        return addresses.size();
    }

    @Override
    public Address getItem(final int position) {
        if (position == addresses.size()) {
            return null;
        }

        return addresses.get(position);
    }

    @Override
    public long getItemId(final int position) {
        if (position == addresses.size()) {
            return 0;
        }

        return WalletUtils.longHash(addresses.get(position).getHash160());
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(final int position, View row, final ViewGroup parent) {
        if (row == null) {
            row = inflater.inflate(R.layout.address_row, null);
        }

        final Address address = getItem(position);
        bindView(row, address);

        return row;
    }

    public void bindView(@Nonnull final View row, @Nonnull final Address address) {
        TextView addressLabel = (TextView) row.findViewById(R.id.address_row_label);
        addressLabel.setText(GenericUtils.addressSplitToGroups(address.toString()));

        TextView addressUsageLabel = (TextView) row.findViewById(R.id.address_row_usage);
        TextView addressUsageFontIcon = (TextView) row.findViewById(R.id.address_row_usage_font_icon);
        Fonts.setTypeface(addressUsageFontIcon, Fonts.Font.COINOMI_FONT_ICONS);

        if (usedAddresses.contains(address)) {
            addressUsageLabel.setText(R.string.previous_addresses_used);
            addressUsageFontIcon.setText(R.string.font_icon_receive_coins);
            addressUsageFontIcon.setBackgroundResource(R.drawable.address_row_cyrcle_bg_used);
        } else {
            addressUsageLabel.setText(R.string.previous_addresses_unused);
            addressUsageFontIcon.setText(R.string.font_icon_checkmark);
            addressUsageFontIcon.setBackgroundResource(R.drawable.address_row_cyrcle_bg_unused);
        }
    }
}
