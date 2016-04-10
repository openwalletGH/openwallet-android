package com.coinomi.wallet.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.coinomi.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class UnlockWalletDialog extends DialogFragment {
    private TextView passwordView;
    private Listener listener;

    public static DialogFragment getInstance() {
        return new UnlockWalletDialog();
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

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View view = inflater.inflate(R.layout.get_password_dialog, null);
        passwordView = (TextView) view.findViewById(R.id.password);

        return new DialogBuilder(getActivity())
                .setTitle(R.string.unlock_wallet_title)
                .setView(view)
                .setNegativeButton(R.string.button_cancel, dismissListener)
                .setPositiveButton(R.string.button_ok, okListener)
                .setCancelable(false)
                .create();
    }

    DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (listener != null) listener.onPassword(passwordView.getText());
            dismissAllowingStateLoss();
        }
    };

    DialogInterface.OnClickListener dismissListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dismissAllowingStateLoss();
        }
    };

    public interface Listener {
        void onPassword(CharSequence password);
    }
}