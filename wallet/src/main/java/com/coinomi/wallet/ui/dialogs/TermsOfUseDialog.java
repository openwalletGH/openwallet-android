package com.coinomi.wallet.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.DialogBuilder;

import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class TermsOfUseDialog extends DialogFragment {
    private Listener listener;

    public static TermsOfUseDialog newInstance() {
        return new TermsOfUseDialog();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof Listener) {
            listener = (Listener) activity;
        }
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final DialogBuilder builder = new DialogBuilder(getActivity());
        builder.setTitle(R.string.terms_of_service_title);
        builder.setMessage(R.string.terms_of_service);

        if (listener != null) {
            DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (listener != null) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                listener.onTermsAgree();
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                                listener.onTermsDisagree();
                                break;
                        }
                    }
                    dismissAllowingStateLoss();
                }
            };
            builder.setNegativeButton(R.string.button_disagree, onClickListener);
            builder.setPositiveButton(R.string.button_agree, onClickListener);
        } else {
            builder.setPositiveButton(R.string.button_ok, null);
        }

        return builder.create();
    }

    public interface Listener {
        void onTermsAgree();
        void onTermsDisagree();
    }
}
