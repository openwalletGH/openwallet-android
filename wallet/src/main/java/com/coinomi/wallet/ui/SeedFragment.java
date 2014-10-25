package com.coinomi.wallet.ui;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Generates and shows a new passphrase
 */
public class SeedFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SeedFragment.class);
    private static final int ENTROPY_DEFAULT = 160;
    private static final int ENTROPY_EXTRA = 256;

    private WelcomeFragment.Listener mListener;
    private String seed;

    public SeedFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_seed, container, false);

        final Button buttonNext = (Button) view.findViewById(R.id.button_next);
        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Clicked restore wallet");
                if (mListener != null) {
                    mListener.onRestoreWallet(seed);
                }
            }
        });

        final TextView mnemonicView = (TextView) view.findViewById(R.id.seed);
        setMnemonic(mnemonicView);

        final CheckBox extraEntropy = (CheckBox) view.findViewById(R.id.extra_entropy);
        extraEntropy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    generateMnemonic(mnemonicView, ENTROPY_EXTRA);
                } else {
                    generateMnemonic(mnemonicView, ENTROPY_DEFAULT);
                }
            }
        });

        final CheckBox backedUpSeed = (CheckBox) view.findViewById(R.id.backed_up_seed);
        backedUpSeed.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                buttonNext.setEnabled(isChecked);
            }
        });

        View.OnClickListener generateNewSeedListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Clicked generate a new seed");
                if (extraEntropy.isChecked()) {
                    generateMnemonic(mnemonicView, ENTROPY_EXTRA);
                } else {
                    generateMnemonic(mnemonicView, ENTROPY_DEFAULT);
                }
            }
        };

        mnemonicView.setOnClickListener(generateNewSeedListener);
        view.findViewById(R.id.seed_regenerate_title).setOnClickListener(generateNewSeedListener);

        return view;
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (WelcomeFragment.Listener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement WelcomeFragment.OnFragmentInteractionListener");
        }
        seed = createSeed(ENTROPY_DEFAULT);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private static String createSeed(int entropySize) {
        List<String> mnemonicWords = Wallet.generateMnemonic(entropySize);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mnemonicWords.size(); i++) {
            sb.append(mnemonicWords.get(i));
            sb.append(' ');
        }
        return sb.toString();
    }

    private void setMnemonic(TextView textView) {
        if (seed != null) {
            textView.setText(seed);
        } else {
            generateMnemonic(textView, ENTROPY_DEFAULT);
        }
    }

    private void generateMnemonic(TextView textView, int entropySize) {
        seed = createSeed(entropySize);
        textView.setText(seed);
    }
}
