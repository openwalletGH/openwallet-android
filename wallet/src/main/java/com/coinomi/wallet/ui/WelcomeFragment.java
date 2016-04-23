package com.coinomi.wallet.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.coinomi.wallet.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class WelcomeFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(WelcomeFragment.class);

    private Listener listener;

    public WelcomeFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_welcome, container, false);

        view.findViewById(R.id.create_wallet).setOnClickListener(getOnCreateListener());
        view.findViewById(R.id.restore_wallet).setOnClickListener(getOnRestoreListener());

        return view;
    }

    private View.OnClickListener getOnCreateListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Clicked create new wallet");
                if (listener != null) {
                    listener.onCreateNewWallet();
                }
            }
        };
    }

    private View.OnClickListener getOnRestoreListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Clicked restore wallet");
                if (listener != null) {
                    listener.onRestoreWallet();
                }
            }
        };
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
        void onCreateNewWallet();
        void onRestoreWallet();
        void onSeedCreated(String seed);
        void onSeedVerified(Bundle args);
    }
}
