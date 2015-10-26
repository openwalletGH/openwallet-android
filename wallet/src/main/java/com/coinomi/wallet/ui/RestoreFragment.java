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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.Keyboard;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
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
    private TextView errorMnemonicΜessage;
    private WelcomeFragment.Listener mListener;
    private boolean isSeedProtected = false;
    private EditText bip39Passphrase;
    private Button skipButton;

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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_restore, container, false);

        Fonts.setTypeface(view.findViewById(R.id.coins_icon), Fonts.Font.COINOMI_FONT_ICONS);
        Fonts.setTypeface(view.findViewById(R.id.warning_icon), Fonts.Font.COINOMI_FONT_ICONS);

        ImageButton scanQrButton = (ImageButton) view.findViewById(R.id.scan_qr_code);
        scanQrButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleScan();
            }
        });

        // Setup auto complete the mnemonic words
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this.getActivity(),
                R.layout.item_simple, MnemonicCode.INSTANCE.getWordList());
        mnemonicTextView = (MultiAutoCompleteTextView) view.findViewById(R.id.seed);
        mnemonicTextView.setAdapter(adapter);
        mnemonicTextView.setTokenizer(new SpaceTokenizer() {
            @Override
            public void onToken() {
                clearError(errorMnemonicΜessage);
            }
        });

        // Restore message
        errorMnemonicΜessage = (TextView) view.findViewById(R.id.restore_message);
        errorMnemonicΜessage.setVisibility(View.GONE);

        bip39Passphrase = (EditText) view.findViewById(R.id.bip39_passphrase);
        final View bip39PassphraseTitle = view.findViewById(R.id.bip39_passphrase_title);

        bip39Passphrase.setVisibility(View.GONE);
        bip39PassphraseTitle.setVisibility(View.GONE);

        // Checkbox to enable/disable password protected seed (BIP39)
        // For new seed
        final View seedProtectInfoNew = view.findViewById(R.id.seed_protect_info);
        seedProtectInfoNew.setVisibility(View.GONE);
        CheckBox seedProtectNew = (CheckBox) view.findViewById(R.id.seed_protect);
        if (!isNewSeed) seedProtectNew.setVisibility(View.GONE);

        // For existing seed
        final View seedProtectInfoExisting = view.findViewById(R.id.restore_seed_protected_info);
        seedProtectInfoExisting.setVisibility(View.GONE);
        final CheckBox seedProtectExisting = (CheckBox) view.findViewById(R.id.restore_seed_protected);
        if (isNewSeed) seedProtectExisting.setVisibility(View.GONE);

        // Generic checkbox and info text
        final View seedProtectInfo;
        final CheckBox seedProtect;

        if (isNewSeed) {
            seedProtectInfo = seedProtectInfoNew;
            seedProtect = seedProtectNew;
        } else {
            seedProtectInfo = seedProtectInfoExisting;
            seedProtect = seedProtectExisting;
        }

        seedProtect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isSeedProtected = isChecked;
                if (isChecked) {
                    skipButton.setVisibility(View.GONE);
                    seedProtectInfo.setVisibility(View.VISIBLE);
                    bip39PassphraseTitle.setVisibility(View.VISIBLE);
                    bip39Passphrase.setVisibility(View.VISIBLE);
                } else {
                    skipButton.setVisibility(View.VISIBLE);
                    seedProtectInfo.setVisibility(View.GONE);
                    bip39PassphraseTitle.setVisibility(View.GONE);
                    bip39Passphrase.setVisibility(View.GONE);
                    bip39Passphrase.setText(null);
                }
            }
        });

        // Skip link
        skipButton = (Button) view.findViewById(R.id.seed_entry_skip);
        if (seed != null) {
            skipButton.setOnClickListener(getOnSkipListener());
            skipButton.setVisibility(View.VISIBLE);
        } else {
            skipButton.setVisibility(View.GONE);
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
                verifyMnemonicAndProceed();
            }
        };
    }

    private View.OnClickListener getOnSkipListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Skipping seed verification.");
                mnemonicTextView.setText("");
                SkipDialogFragment skipDialog = SkipDialogFragment.newInstance(seed);
                skipDialog.show(getFragmentManager(), null);
            }
        };
    }

    private void verifyMnemonicAndProceed() {
        Keyboard.hideKeyboard(getActivity());
        if (verifyMnemonic()) {
            Bundle args = getArguments();
            if (args == null) args = new Bundle();

            if (isSeedProtected) {
                args.putString(Constants.ARG_SEED_PASSWORD, bip39Passphrase.getText().toString());
            }
            args.putString(Constants.ARG_SEED, mnemonicTextView.getText().toString().trim());
            if (mListener != null) mListener.onSeedVerified(args);
        }
    }

    private boolean verifyMnemonic() {
        log.info("Verifying seed");
        // TODO, use util class to be ported from the NXT branch
        String seedText = mnemonicTextView.getText().toString().trim();
        ArrayList<String> seedWords = new ArrayList<>();
        for (String word : seedText.trim().split(" ")) {
            if (word.isEmpty()) continue;
            seedWords.add(word);
        }
        boolean isSeedValid = false;
        try {
            MnemonicCode.INSTANCE.check(seedWords);
            clearError(errorMnemonicΜessage);
            isSeedValid = true;
        } catch (MnemonicException.MnemonicChecksumException e) {
            log.info("Checksum error in seed: {}", e.getMessage());
            setError(errorMnemonicΜessage, R.string.restore_error_checksum);
        } catch (MnemonicException.MnemonicWordException e) {
            log.info("Unknown words in seed: {}", e.getMessage());
            setError(errorMnemonicΜessage, R.string.restore_error_words);
        } catch (MnemonicException e) {
            log.info("Error verifying seed: {}", e.getMessage());
            setError(errorMnemonicΜessage, R.string.restore_error, e.getMessage());
        }

        if (isSeedValid && seed != null && !seedText.equals(seed.trim())) {
            log.info("Typed seed does not match the generated one.");
            setError(errorMnemonicΜessage, R.string.restore_error_mismatch);
            isSeedValid = false;
        }
        return isSeedValid;
    }

    public static class SkipDialogFragment extends DialogFragment {

        private WelcomeFragment.Listener mListener;

        public static SkipDialogFragment newInstance(String seed) {
            SkipDialogFragment newDialog = new SkipDialogFragment();
            Bundle args = new Bundle();
            args.putString(Constants.ARG_SEED, seed);
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
                           if (mListener != null) mListener.onSeedVerified(getArguments());
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
