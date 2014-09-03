package com.coinomi.wallet.ui;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Generates and shows a new passphrase
 */
public class PassphraseFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(PassphraseFragment.class);
    private static final int ENTROPY_160 = 160;
    private static final int ENTROPY_256 = 256;

    private WelcomeFragment.OnFragmentInteractionListener mListener;

    public PassphraseFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_passphrase, container, false);

        Fonts.setTypeface(view.findViewById(R.id.passphrase_info), Fonts.Font.ROBOTO_REGULAR);
        Fonts.setTypeface(view.findViewById(R.id.mnemonic), Fonts.Font.ROBOTO_LIGHT);

        view.findViewById(R.id.button_next).setOnClickListener(getOnRestoreListener());

        final TextView mnemonicView = (TextView) view.findViewById(R.id.mnemonic);
        generateMnemonic(mnemonicView, ENTROPY_160);

        final CheckBox extraEntropy = (CheckBox) view.findViewById(R.id.extra_entropy);
        extraEntropy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    generateMnemonic(mnemonicView, ENTROPY_256);
                } else {
                    generateMnemonic(mnemonicView, ENTROPY_160);
                }
            }
        });

        return view;
    }

    private View.OnClickListener getOnRestoreListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Clicked restore wallet");
                if (mListener != null) {
                    mListener.onRestoreWallet();
                }
            }
        };
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (WelcomeFragment.OnFragmentInteractionListener) activity;
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

    private void generateMnemonic(TextView mnemonicView, int entropyBits) {
        try {
            List<String> mnemonicWords = Wallet.generateMnemonic(entropyBits);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mnemonicWords.size(); i++) {
                sb.append(mnemonicWords.get(i));
                sb.append(' ');
            }
            mnemonicView.setText(sb.toString());
        } catch (IOException e) {
            Toast.makeText(this.getActivity(), "Error creating a mnemonic: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
