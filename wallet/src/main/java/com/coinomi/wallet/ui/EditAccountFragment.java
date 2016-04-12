package com.coinomi.wallet.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;

import butterknife.ButterKnife;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.wallet.Constants.ARG_ACCOUNT_ID;

/**
 * @author John L. Jegutanis
 */
public final class EditAccountFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = EditAccountFragment.class.getName();
    public static void edit(final FragmentManager fm, WalletAccount account) {
        final DialogFragment newFragment = EditAccountFragment.instance(account);
        newFragment.show(fm, FRAGMENT_TAG);
    }

    private static EditAccountFragment instance(WalletAccount account) {
        final EditAccountFragment fragment = new EditAccountFragment();

        final Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT_ID, account.getId());
        fragment.setArguments(args);

        return fragment;
    }

    private Context context;
    private WalletApplication app;
    private Listener listener;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.context = context;
        this.app = (WalletApplication) context.getApplicationContext();
        try {
            listener = (Listener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " + Listener.class);
        }
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final WalletAccount account = checkNotNull(app.getAccount(args.getString(ARG_ACCOUNT_ID)));
        final LayoutInflater inflater = LayoutInflater.from(context);
        final DialogBuilder dialog = new DialogBuilder(context);
        final View view = inflater.inflate(R.layout.edit_account_dialog, null);
        final EditText descriptionView = ButterKnife.findById(view, R.id.edit_account_description);
        descriptionView.setText(account.getDescription());
        descriptionView.setHint(account.getCoinType().getName());

        dialog.setTitle(R.string.edit_account_title);
        dialog.setView(view);

        final DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    final String newDescription = descriptionView.getText().toString().trim();
                    account.setDescription(newDescription);
                    if (listener != null) listener.onAccountModified(account);
                }

                dismiss();
            }
        };

        dialog.setPositiveButton(R.string.button_save, onClickListener);
        dialog.setNegativeButton(R.string.button_cancel, onClickListener);

        return dialog.create();
    }

    public interface Listener {
        void onAccountModified(WalletAccount account);
    }
}
