package com.coinomi.wallet.ui;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.QrCodeButton;
import com.coinomi.wallet.util.Keyboard;
import com.google.bitcoin.crypto.MnemonicCode;
import com.google.bitcoin.crypto.MnemonicException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import javax.annotation.Nullable;

/**
 * A simple {@link Fragment} subclass.
 *
 */
public class RestoreFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(RestoreFragment.class);
    private static final int REQUEST_CODE_SCAN = 0;

    private MultiAutoCompleteTextView mnemonicTextView;
    @Nullable private String seed;
    private boolean isNewSeed;
    private TextView restoreΜessage;
    private int colorSignificant;
    private int colorInsignificant;
    private int colorError;
    private WelcomeFragment.Listener mListener;

    public static RestoreFragment newInstance() {
        return newInstance(null);
    }

    public static RestoreFragment newInstance(@Nullable String seed) {
        RestoreFragment fragment = new RestoreFragment();
        if (seed != null) {
            Bundle args = new Bundle();
            args.putString(Constants.ARG_SEED, seed);
            fragment.setArguments(args);
        }
        return fragment;
    }

    public RestoreFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            seed = getArguments().getString(Constants.ARG_SEED);
            isNewSeed = seed != null;
        }

        colorInsignificant = getResources().getColor(R.color.gray_26_hint_text);
        colorError = getResources().getColor(R.color.fg_error);
    }

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
                R.layout.item_simple, MnemonicCode.INSTANCE.getWordList());
        mnemonicTextView = (MultiAutoCompleteTextView) view.findViewById(R.id.seed);
        mnemonicTextView.setAdapter(adapter);
        mnemonicTextView.setTokenizer(new SpaceTokenizer() {
            @Override
            public void onToken() {
                clearRestorationMessage();
            }
        });

        // FIXME causes problems in older Androids
        // Show keyboard
//        Keyboard.focusAndShowKeyboard(mnemonicTextView, getActivity());

        // Restore message
        restoreΜessage = (TextView) view.findViewById(R.id.restore_message);
        restoreΜessage.setVisibility(View.GONE);

        // Skip link
        View skip = view.findViewById(R.id.seed_entry_skip);
        if (seed != null) {
            skip.setOnClickListener(getOnSkipListener());
            skip.setVisibility(View.VISIBLE);
        } else {
            skip.setVisibility(View.GONE);
        }

        // Next button
        view.findViewById(R.id.button_next).setOnClickListener(getOnNextListener());

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
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private View.OnClickListener getOnNextListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verifyMnemonic();
            }
        };
    }

    private View.OnClickListener getOnSkipListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Skipping seed verification.");
                mnemonicTextView.setText("");
                verifyMnemonic(seed, true);
            }
        };
    }

    private void verifyMnemonic() {
        verifyMnemonic(mnemonicTextView.getText().toString(), false);
    }

    private void verifyMnemonic(String seedText, boolean skipSeedEntry) {
        log.info("Verifying seed");
        ArrayList<String> seedWords = new ArrayList<String>();
        for (String word : seedText.trim().split(" ")) {
            if (word.isEmpty()) continue;
            seedWords.add(word);
        }
        boolean isValid = false;
        try {
            MnemonicCode.INSTANCE.check(seedWords);
            clearRestorationMessage();
            isValid = true;
        } catch (MnemonicException.MnemonicChecksumException e) {
            log.info("Checksum error in seed: {}", e.getMessage());
            setVerificationError(R.string.restore_error_checksum);
        } catch (MnemonicException.MnemonicWordException e) {
            log.info("Unknown words in seed: {}", e.getMessage());
            setVerificationError(R.string.restore_error_words);
        } catch (MnemonicException e) {
            log.info("Error verifying seed: {}", e.getMessage());
            setVerificationError(R.string.restore_error, e.getMessage());
        }

        if (seed != null && !seedText.trim().equals(seed.trim())) {
            log.info("Typed seed does not match the generated one.");
            setVerificationError(R.string.restore_error_mismatch);
            isValid = false;
        }

        if (isValid && skipSeedEntry) {
            SkipDialogFragment skipDialog = SkipDialogFragment.newInstance(seedText);
            skipDialog.show(getFragmentManager(), null);
        } else if (isValid && isNewSeed) {
            if (mListener != null) mListener.onSetPassword(seedText);
        } else if (isValid) {
            if (mListener != null) mListener.onConfirmPassword(seedText);
        }
    }

    public static class SkipDialogFragment extends DialogFragment {

        private WelcomeFragment.Listener mListener;

        public static SkipDialogFragment newInstance(String seed) {
            SkipDialogFragment newDialog = new SkipDialogFragment();
            Bundle args = new Bundle();
            args.putSerializable(Constants.ARG_SEED, seed);
            newDialog.setArguments(args);
            return newDialog;
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
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String seed = getArguments().getString(Constants.ARG_SEED);
            // FIXME does not look good with custom dialogs in older Samsungs
//            View dialogView = getActivity().getLayoutInflater().inflate(R.layout.skip_seed_dialog, null);
//            TextView seedView = (TextView) dialogView.findViewById(R.id.seed);
//            seedView.setText(seed);

            String dialogMessage = getResources().getString(R.string.restore_skip_info1) + "\n\n" +
                    seed + "\n\n" + getResources().getString(R.string.restore_skip_info2);
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.restore_skip_title)
//                   .setView(dialogView) FIXME
                   .setMessage(dialogMessage)
                   .setPositiveButton(R.string.button_skip, new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialog, int which) {
                           if (mListener != null) mListener.onSetPassword(seed);
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

    private void setVerificationError(int messageId, Object... formatArgs) {
        setVerificationError(getResources().getString(messageId, formatArgs));
    }

    private void setVerificationError(String message) {
        restoreΜessage.setText(message);
        restoreΜessage.setVisibility(View.VISIBLE);
    }

    private void clearRestorationMessage() {
        if (restoreΜessage.isShown()) restoreΜessage.setVisibility(View.GONE);
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

    private abstract static class SpaceTokenizer implements MultiAutoCompleteTextView.Tokenizer {
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
            onToken();
            return text + " ";
        }

        abstract public void onToken();
    }
}
