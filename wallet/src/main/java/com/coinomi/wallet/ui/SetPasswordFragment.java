package com.coinomi.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.Keyboard;
import com.coinomi.wallet.util.PasswordQualityChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fragment that sets a password
 */
public class SetPasswordFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SetPasswordFragment.class);

    private Listener listener;
    private boolean isPasswordGood;
    private boolean isPasswordsMatch;
    private PasswordQualityChecker passwordQualityChecker;
    private EditText password1;
    private EditText password2;
    private TextView errorPassword;
    private TextView errorPasswordsMismatch;

    public static SetPasswordFragment newInstance(Bundle args) {
        SetPasswordFragment fragment = new SetPasswordFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public SetPasswordFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        passwordQualityChecker = new PasswordQualityChecker(getActivity());
        isPasswordGood = false;
        isPasswordsMatch = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_set_password, container, false);

        Fonts.setTypeface(view.findViewById(R.id.key_icon), Fonts.Font.COINOMI_FONT_ICONS);

        errorPassword = (TextView) view.findViewById(R.id.password_error);
        errorPasswordsMismatch = (TextView) view.findViewById(R.id.passwords_mismatch);

        clearError(errorPassword);
        clearError(errorPasswordsMismatch);

        password1 = (EditText) view.findViewById(R.id.password1);
        password2 = (EditText) view.findViewById(R.id.password2);

        password1.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View textView, boolean hasFocus) {
                if (hasFocus) {
                    clearError(errorPassword);
                } else {
                    checkPasswordQuality();
                }
            }
        });

        password2.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View textView, boolean hasFocus) {
                if (hasFocus) {
                    clearError(errorPasswordsMismatch);
                } else {
                    checkPasswordsMatch();
                }
            }
        });

        // Next button
        Button finishButton = (Button) view.findViewById(R.id.button_next);
        finishButton.setOnClickListener(getOnFinishListener());
        finishButton.setImeOptions(EditorInfo.IME_ACTION_DONE);

        // Skip link
        view.findViewById(R.id.password_skip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogFragment dialog = SkipPasswordDialogFragment.newInstance(getArguments());
                dialog.show(getFragmentManager(), null);
            }
        });

        return view;
    }

    private void checkPasswordQuality() {
        String pass = password1.getText().toString();
        isPasswordGood = false;
        try {
            passwordQualityChecker.checkPassword(pass);
            isPasswordGood = true;
            clearError(errorPassword);
        } catch (PasswordQualityChecker.PasswordTooCommonException e1) {
            log.info("Entered a too common password {}", pass);
            setError(errorPassword, R.string.password_too_common_error, pass);
        } catch (PasswordQualityChecker.PasswordTooShortException e2) {
            log.info("Entered a too short password");
            setError(errorPassword, R.string.password_too_short_error,
                    passwordQualityChecker.getMinPasswordLength());
        }
        log.info("Password good = {}", isPasswordGood);
    }

    private void checkPasswordsMatch() {
        String pass1 = password1.getText().toString();
        String pass2 = password2.getText().toString();
        isPasswordsMatch = pass1.equals(pass2);
        if (!isPasswordsMatch) showError(errorPasswordsMismatch);
        log.info("Passwords match = {}", isPasswordsMatch);
    }

    private View.OnClickListener getOnFinishListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Keyboard.hideKeyboard(getActivity());
                checkPasswordQuality();
                checkPasswordsMatch();
                if (isPasswordGood && isPasswordsMatch) {
                    Bundle args = getArguments();
                    args.putString(Constants.ARG_PASSWORD, password1.getText().toString());
                    listener.onPasswordSet(args);
                } else {
                    Toast.makeText(SetPasswordFragment.this.getActivity(),
                            R.string.password_error, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    public static class SkipPasswordDialogFragment extends DialogFragment {
        private Listener mListener;

        public static SkipPasswordDialogFragment newInstance(Bundle args) {
            SkipPasswordDialogFragment dialog = new SkipPasswordDialogFragment();
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            try {
                mListener = (Listener) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException(activity.toString()
                        + " must implement " + Listener.class);
            }
        }
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setMessage(getResources().getString(R.string.password_skip_warn))
                    .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                            Keyboard.hideKeyboard(getActivity());
                            getArguments().putString(Constants.ARG_PASSWORD, "");
                            mListener.onPasswordSet(getArguments());
                        }
                    })
                    .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                        }
                    });
            return builder.create();
        }
    }

    private void setError(TextView errorView, int messageId, Object... formatArgs) {
        setError(errorView, getResources().getString(messageId, formatArgs));
    }

    private void setError(TextView errorView, String message) {
        errorView.setText(message);
        showError(errorView);
    }

    private void showError(TextView errorView) {
        errorView.setVisibility(View.VISIBLE);
    }

    private void clearError(TextView errorView) {
        errorView.setVisibility(View.GONE);
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            listener = (Listener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " + Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface Listener {
        void onPasswordSet(Bundle args);
    }
}
