package com.coinomi.wallet.ui;


import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.QrCodeButton;
import com.coinomi.wallet.util.Keyboard;
import com.google.bitcoin.crypto.MnemonicException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * A simple {@link Fragment} subclass.
 *
 */
public class RestoreFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(RestoreFragment.class);
    private static final int REQUEST_CODE_SCAN = 0;

    private VerifyPassphraseTask verifyTask;
    private MultiAutoCompleteTextView mnemonicTextView;

    public RestoreFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_restore, container, false);

        QrCodeButton scanQrButton = (QrCodeButton) view.findViewById(R.id.scan_qr_code);
        scanQrButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleScan();
            }
        });

        // Setup auto complete the mnemonic words
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this.getActivity(),
                android.R.layout.simple_dropdown_item_1line, Wallet.mnemonicCode.getWordList());
        mnemonicTextView = (MultiAutoCompleteTextView) view.findViewById(R.id.mnemonic);
        mnemonicTextView.setAdapter(adapter);
        mnemonicTextView.setTokenizer(new SpaceTokenizer());

        Keyboard.focusAndShowKeyboard(mnemonicTextView, getActivity());

        view.findViewById(R.id.button_next).setOnClickListener(getOnFinishListener());

        return view;
    }

    private View.OnClickListener getOnFinishListener() {
        return new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                log.info("Clicked finish restoring wallet");

                verifyMnemonic();
            }
        };
    }

    private void verifyMnemonic() {
        if (verifyTask == null || verifyTask.getStatus() == AsyncTask.Status.FINISHED) {
            verifyTask = new VerifyPassphraseTask();
            verifyTask.execute(mnemonicTextView.getText().toString());
        }
    }

    public static class VerifyDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setMessage(getResources().getString(R.string.restore_verifying));
            dialog.setCancelable(false);
            return dialog;
        }
    }

    private class VerifyPassphraseTask extends AsyncTask<String, Void, Wallet> {
        private VerifyDialogFragment verifyDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            verifyDialog = new VerifyDialogFragment();
            verifyDialog.show(RestoreFragment.this.getFragmentManager(), null);

        }

        protected Wallet doInBackground(String... passphraseText) {
            ArrayList<String> passphrase = new ArrayList<String>();
            for (String word : passphraseText[0].trim().split(" ")) {
                if (word.isEmpty()) continue;
                passphrase.add(word);
            }
            Wallet wallet = null;
            try {
                wallet = new Wallet(passphrase, null);
                wallet.createCoinPockets(Constants.DEFAULT_COINS, true);
                getWalletApplication().setWallet(wallet);
                getWalletApplication().saveWalletNow();
            } catch (MnemonicException e) {
                log.error("Error creating a wallet", e);
            }
            return wallet;
        }

        protected void onPostExecute(Wallet wallet) {
            verifyDialog.dismiss();
            if (wallet != null) {
                RestoreFragment.this.mnemonicTextView.setText("");
                finalizeWalletCreation();
            }
            else {
                Toast.makeText(RestoreFragment.this.getActivity(), R.string.restore_error,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    WalletApplication getWalletApplication() {
        return (WalletApplication) getActivity().getApplication();
    }

    private void finalizeWalletCreation() {
        startActivity(new Intent(getActivity(), WalletActivity.class));
        getActivity().finish();
    }

    private void handleScan() {
        startActivityForResult(new Intent(getActivity(), ScanActivity.class), REQUEST_CODE_SCAN);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                mnemonicTextView.setText(intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT));
                verifyMnemonic();
            }
        }
    }

    private static class SpaceTokenizer implements MultiAutoCompleteTextView.Tokenizer {
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;

            while (i > 0 && text.charAt(i - 1) != ' ') {
                i--;
            }

            return i;
        }

        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();

            while (i < len) {
                if (text.charAt(i) == ' ') {
                    return i;
                } else {
                    i++;
                }
            }

            return len;
        }

        public CharSequence terminateToken(CharSequence text) {
            return text + " ";
        }
    }
}
