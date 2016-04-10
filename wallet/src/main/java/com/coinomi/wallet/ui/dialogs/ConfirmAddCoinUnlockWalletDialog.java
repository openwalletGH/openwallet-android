package com.coinomi.wallet.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.DialogBuilder;
import com.coinomi.wallet.ui.UnlockWalletDialog;

/**
 * @author John L. Jegutanis
 */
public class ConfirmAddCoinUnlockWalletDialog extends DialogFragment {
    private static final String ADD_COIN_NAME = "add_coin_name";
    private Listener listener;

    public static DialogFragment getInstance(String coinName) {
        DialogFragment dialog = new ConfirmAddCoinUnlockWalletDialog();
        dialog.setArguments(new Bundle());
        dialog.getArguments().putString(ADD_COIN_NAME, coinName);
        return dialog;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.getClass() + " must implement " + Listener.class);
        }
    }

    @Override
    public void onDetach() {
        listener = null;
        super.onDetach();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View view = inflater.inflate(R.layout.get_password_dialog, null);
        final TextView passwordView = (TextView) view.findViewById(R.id.password);
        final String coinName = getArguments().getString(ADD_COIN_NAME);

        return new DialogBuilder(getActivity())
                .setTitle(getString(R.string.adding_coin_confirmation_title, coinName))
                .setView(view)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) listener.addCoin(passwordView.getText());
                    }
                }).create();
    }

    public interface Listener {
        void addCoin(CharSequence password);
    }
}