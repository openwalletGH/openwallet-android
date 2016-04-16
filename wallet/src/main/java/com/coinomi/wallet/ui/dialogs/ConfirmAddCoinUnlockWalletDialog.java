package com.coinomi.wallet.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.DialogBuilder;

import butterknife.ButterKnife;

/**
 * @author John L. Jegutanis
 */
public class ConfirmAddCoinUnlockWalletDialog extends DialogFragment {
    private static final String ADD_COIN = "add_coin";
    private static final String ASK_PASSWORD = "ask_password";
    private Listener listener;

    public static DialogFragment getInstance(CoinType type, boolean askPassword) {
        DialogFragment dialog = new ConfirmAddCoinUnlockWalletDialog();
        dialog.setArguments(new Bundle());
        dialog.getArguments().putSerializable(ADD_COIN, type);
        dialog.getArguments().putBoolean(ASK_PASSWORD, askPassword);
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
        final CoinType type = (CoinType) getArguments().getSerializable(ADD_COIN);
        final boolean askPassword = getArguments().getBoolean(ASK_PASSWORD);
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View view = inflater.inflate(R.layout.add_account_dialog, null);
        final TextView passwordMessage = ButterKnife.findById(view, R.id.password_message);
        final EditText password = ButterKnife.findById(view, R.id.password);
        final EditText description = ButterKnife.findById(view, R.id.edit_account_description);

        if (!askPassword) {
            passwordMessage.setVisibility(View.GONE);
            password.setVisibility(View.GONE);
        }

        return new DialogBuilder(getActivity())
                .setTitle(getString(R.string.adding_coin_confirmation_title, type.getName()))
                .setView(view)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.addCoin(type, description.getText().toString(), password.getText());
                        }
                    }
                }).create();
    }

    public interface Listener {
        void addCoin(CoinType type, String description, CharSequence password);
    }
}