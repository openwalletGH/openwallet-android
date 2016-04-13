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

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.R;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public final class EditAddressBookEntryFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = EditAddressBookEntryFragment.class.getName();

    private static final String KEY_COIN_ID = "coin_id";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_SUGGESTED_ADDRESS_LABEL = "suggested_address_label";

    public static void edit(final FragmentManager fm, @Nonnull final AbstractAddress address) {
        edit(fm, address.getType(), address, null);
    }

    public static void edit(final FragmentManager fm, @Nonnull CoinType type,
                            @Nonnull final AbstractAddress address) {
        edit(fm, type, address, null);
    }

    public static void edit(final FragmentManager fm, @Nonnull CoinType type,
                            @Nonnull final AbstractAddress address,
                            @Nullable final String suggestedAddressLabel) {
        final DialogFragment newFragment =
                EditAddressBookEntryFragment.instance(type, address, suggestedAddressLabel);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private static EditAddressBookEntryFragment instance(@Nonnull CoinType type,
                                                         @Nonnull final AbstractAddress address,
                                                         @Nullable final String suggestedAddressLabel) {
        final EditAddressBookEntryFragment fragment = new EditAddressBookEntryFragment();

        final Bundle args = new Bundle();
        args.putString(KEY_COIN_ID, type.getId());
        args.putSerializable(KEY_ADDRESS, address);
        args.putString(KEY_SUGGESTED_ADDRESS_LABEL, suggestedAddressLabel);
        fragment.setArguments(args);

        return fragment;
    }

    private Context context;
    private ContentResolver contentResolver;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.context = context;
        this.contentResolver = context.getContentResolver();
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final CoinType type = CoinID.typeFromId(args.getString(KEY_COIN_ID));
        final AbstractAddress address = (AbstractAddress) args.getSerializable(KEY_ADDRESS);
        final String suggestedAddressLabel = args.getString(KEY_SUGGESTED_ADDRESS_LABEL);

        final LayoutInflater inflater = LayoutInflater.from(context);

        final Uri uri = AddressBookProvider.contentUri(context.getPackageName(), type)
                .buildUpon().appendPath(address.toString()).build();

        final String label = AddressBookProvider.resolveLabel(context, address);

        final boolean isAdd = label == null;

        final DialogBuilder dialog = new DialogBuilder(context);

        final View view = inflater.inflate(R.layout.edit_address_book_entry_dialog, null);

        final TextView viewAddress = (TextView) view.findViewById(R.id.edit_address_book_entry_address);
        viewAddress.setText(GenericUtils.addressSplitToGroups(address));

        final TextView viewLabel = (TextView) view.findViewById(R.id.edit_address_book_entry_label);
        viewLabel.setText(label != null ? label : suggestedAddressLabel);

        dialog.setView(view);

        final DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    final String newLabel = viewLabel.getText().toString().trim();

                    if (!newLabel.isEmpty()) {
                        final ContentValues values = new ContentValues();
                        values.put(AddressBookProvider.KEY_LABEL, newLabel);

                        if (isAdd)
                            contentResolver.insert(uri, values);
                        else
                            contentResolver.update(uri, values, null, null);
                    } else if (!isAdd) {
                        contentResolver.delete(uri, null, null);
                    }
                } else if (which == DialogInterface.BUTTON_NEUTRAL) {
                    contentResolver.delete(uri, null, null);
                }

                dismiss();
            }
        };

        dialog.setPositiveButton(R.string.button_save, onClickListener);
        if (!isAdd)
            dialog.setNeutralButton(R.string.button_delete, onClickListener);
        dialog.setNegativeButton(R.string.button_cancel, onClickListener);

        return dialog.create();
    }
}
