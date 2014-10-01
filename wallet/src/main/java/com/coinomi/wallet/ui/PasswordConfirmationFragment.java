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

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;

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
    @Nullable private String seed;

    private Listener mListener;

    static PasswordConfirmationFragment newInstance() {
        PasswordConfirmationFragment fragment = new PasswordConfirmationFragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }


    public static Fragment newWalletRestoration(String seed) {
        Fragment fragment = new PasswordConfirmationFragment();
        fragment.getArguments().putString(Constants.ARG_SEED, seed);
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
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password_confirmation, container, false);

        final EditText password = (EditText) view.findViewById(R.id.password);

        view.findViewById(R.id.button_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
