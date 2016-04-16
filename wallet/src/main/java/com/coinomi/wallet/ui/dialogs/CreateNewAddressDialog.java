package com.coinomi.wallet.ui.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.exceptions.Bip44KeyLookAheadExceededException;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletPocketHD;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.DialogBuilder;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public class CreateNewAddressDialog extends DialogFragment {
    private WalletApplication app;
    @Nullable private ContentResolver resolver;

    public static DialogFragment getInstance(WalletAccount account) {
        DialogFragment dialog = new CreateNewAddressDialog();
        dialog.setArguments(new Bundle());
        dialog.getArguments().putString(Constants.ARG_ACCOUNT_ID, account.getId());
        return dialog;
    }

    public CreateNewAddressDialog() { }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        app = (WalletApplication) activity.getApplication();
        resolver = activity.getContentResolver();
    }

    @Override
    public void onDetach() {
        resolver = null;
        super.onDetach();
    }

    @Override
    public Dialog onCreateDialog(Bundle state) {
        Dialog dialog;
        DialogInterface.OnClickListener dismissListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissAllowingStateLoss();
            }
        };
        WalletAccount account = app.getAccount(getArguments().getString(Constants.ARG_ACCOUNT_ID));

        // Only WalletPocketHD can create new addresses
        if (account != null && account instanceof WalletPocketHD) {
            final WalletPocketHD pocketHD = (WalletPocketHD) account;
            if (pocketHD.canCreateFreshReceiveAddress()) {
                final LayoutInflater inflater = LayoutInflater.from(getActivity());
                final View view = inflater.inflate(R.layout.new_address_dialog, null);
                final TextView viewLabel = (TextView) view.findViewById(R.id.new_address_label);

                final DialogBuilder builder = new DialogBuilder(getActivity());
                builder.setView(view);
                builder.setNegativeButton(R.string.button_cancel, dismissListener);
                builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createAddress(pocketHD, viewLabel.getText().toString().trim());
                        dismissAllowingStateLoss();
                    }
                });
                dialog = builder.create();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.too_many_unused_addresses)
                        .setPositiveButton(R.string.button_ok, dismissListener);
                dialog = builder.create();
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.error_generic)
                    .setPositiveButton(R.string.button_ok, dismissListener);
            dialog = builder.create();
        }
        return dialog;
    }

    private void createAddress(WalletPocketHD account, @Nullable String newLabel) {
        if (account.canCreateFreshReceiveAddress()) {
            try {
                AbstractAddress newAddress = account.getFreshReceiveAddress(
                        app.getConfiguration().isManualAddressManagement());

                if (newLabel != null && !newLabel.isEmpty()) {
                    final Uri uri =
                            AddressBookProvider.contentUri(getActivity().getPackageName(), account.getCoinType())
                                    .buildUpon().appendPath(newAddress.toString()).build();
                    final ContentValues values = new ContentValues();
                    values.put(AddressBookProvider.KEY_LABEL, newLabel);
                    if (resolver != null) resolver.insert(uri, values);
                }
            } catch (Bip44KeyLookAheadExceededException e) {
                // Should not happen as we already checked if we can create a new address
                Toast.makeText(getActivity(), R.string.too_many_unused_addresses, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getContext(), R.string.too_many_unused_addresses, Toast.LENGTH_LONG).show();
        }
    }
}
