package com.coinomi.wallet.ui;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
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
    @Nullable private String seed;
    private Listener mListener;

    static PasswordConfirmationFragment newInstance(String message) {
        PasswordConfirmationFragment fragment = new PasswordConfirmationFragment();
        fragment.setArguments(new Bundle());
        fragment.getArguments().putString(Constants.ARG_MESSAGE, message);
        return fragment;
    }

    public static Fragment newWalletRestoration(String seed, String message) {
        Fragment fragment = new PasswordConfirmationFragment();
        fragment.getArguments().putString(Constants.ARG_SEED, seed);
        fragment.getArguments().putString(Constants.ARG_MESSAGE, message);
        return fragment;
    }
    public PasswordConfirmationFragment() {
        setArguments(new Bundle());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            seed = getArguments().getString(Constants.ARG_SEED);
            message = getArguments().getString(Constants.ARG_MESSAGE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password_confirmation, container, false);

        TextView messageView = (TextView) view.findViewById(R.id.message);
        if (message != null) {
            messageView.setText(message);
        } else {
            messageView.setVisibility(View.GONE);
        }

        final EditText password = (EditText) view.findViewById(R.id.password);
        Keyboard.focusAndShowKeyboard(password, getActivity());

        view.findViewById(R.id.button_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Keyboard.hideKeyboard(getActivity());
                if (mListener != null) {
                    getArguments().putString(Constants.ARG_PASSWORD, password.getText().toString());
                    mListener.onPasswordConfirmed(getArguments());
                }
            }
        });

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface Listener {
        public void onPasswordConfirmed(Bundle args);
    }

}
