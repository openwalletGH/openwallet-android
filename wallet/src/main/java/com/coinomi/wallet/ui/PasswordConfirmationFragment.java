package com.coinomi.wallet.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.Keyboard;

import javax.annotation.Nullable;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link com.coinomi.wallet.ui.PasswordConfirmationFragment.Listener} interface
 * to handle interaction events.
 * Use the {@link PasswordConfirmationFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class PasswordConfirmationFragment extends Fragment {
    @Nullable private String message;
    private Listener listener;

    static PasswordConfirmationFragment newInstance(String message) {
        return newInstance(message, null);
    }

    static PasswordConfirmationFragment newInstance(String message, @Nullable Bundle args) {
        PasswordConfirmationFragment fragment = new PasswordConfirmationFragment();
        fragment.setArguments(args != null ? args : new Bundle());
        fragment.getArguments().putString(Constants.ARG_MESSAGE, message);
        return fragment;
    }

    public PasswordConfirmationFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            message = getArguments().getString(Constants.ARG_MESSAGE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password_confirmation, container, false);

        Fonts.setTypeface(view.findViewById(R.id.key_icon), Fonts.Font.COINOMI_FONT_ICONS);

        TextView messageView = (TextView) view.findViewById(R.id.message);
        if (message != null) {
            messageView.setText(message);
        } else {
            messageView.setVisibility(View.GONE);
        }

        final EditText password = (EditText) view.findViewById(R.id.password);

        view.findViewById(R.id.button_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Keyboard.hideKeyboard(getActivity());
                if (listener != null) {
                    Bundle args = getArguments() == null ? new Bundle() : getArguments();
                    args.remove(Constants.ARG_MESSAGE);
                    args.putString(Constants.ARG_PASSWORD, password.getText().toString());
                    listener.onPasswordConfirmed(args);
                }
            }
        });

        return view;
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
        void onPasswordConfirmed(Bundle args);
    }
}
