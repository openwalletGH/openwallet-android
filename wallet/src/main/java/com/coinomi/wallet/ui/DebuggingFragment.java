package com.coinomi.wallet.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.spongycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.Arrays;

import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
public class DebuggingFragment extends Fragment {
    private static final String PROCESSING_DIALOG_TAG = "processing_dialog_tag";
    private static final String PASSWORD_DIALOG_TAG = "password_dialog_tag";

    private CharSequence password;
    private PasswordTestTask passwordTestTask;
    private Wallet wallet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true); // for the async task
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_debugging, container, false);
        ButterKnife.bind(this, view);

        return view;
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        WalletApplication application = (WalletApplication) context.getApplicationContext();
        wallet = application.getWallet();
    }

    @OnClick(R.id.button_execute_password_test)
    void onExecutePasswordTest() {
        if (wallet.isEncrypted()) {
            showUnlockDialog();
        } else {
            DialogBuilder.warn(getActivity(), R.string.wallet_is_not_locked_message)
                    .setPositiveButton(R.string.button_ok, null)
                    .create().show();
        }
    }

    private void showUnlockDialog() {
        Dialogs.dismissAllowingStateLoss(getFragmentManager(), PASSWORD_DIALOG_TAG);
        UnlockWalletDialog.getInstance().show(getFragmentManager(), PASSWORD_DIALOG_TAG);
    }


    public void setPassword(CharSequence password) {
        this.password = password;
        maybeStartPasswordTestTask();
    }

    private void maybeStartPasswordTestTask() {
        if (passwordTestTask == null) {
            passwordTestTask = new PasswordTestTask();
            passwordTestTask.execute();
        }
    }

    private class PasswordTestTask extends AsyncTask<Void, Void, Void> {
        UnlockResult result = new UnlockResult();
        Exception error;

        @Override
        protected void onPreExecute() {
            Dialogs.ProgressDialogFragment.show(getFragmentManager(),
                    getString(R.string.seed_working), PROCESSING_DIALOG_TAG);
        }

        @Override
        protected Void doInBackground(Void... params) {
            DeterministicKey masterKey = wallet.getMasterKey();
            try {
                if (masterKey.getKeyCrypter() != null) {

                    if (tryDecrypt(masterKey, password)) {
                        result.isUnlockSuccess = true;
                    } else {
                        tryNormalizationDecryption(masterKey, password, result);
                    }

                    if (!result.isUnlockSuccess) {
                        String trimmedPassword = password.toString().trim();
                        if (!password.equals(trimmedPassword)) {
                            if (tryDecrypt(masterKey, trimmedPassword)) {
                                result.isUnlockSuccess = true;
                                result.isWhitespaceTrimmed = true;
                            } else {
                                tryNormalizationDecryption(masterKey, password, result);
                                result.isWhitespaceTrimmed = true;
                            }
                        }
                    }
                } else {
                    throw new RuntimeException("Missing key crypter");
                }
            } catch (Exception e) {
                error = e;
            }

            return null;
        }

        private boolean tryNormalizationDecryption(DeterministicKey masterKey, CharSequence password, UnlockResult result) {
            if (tryDecrypt(masterKey, Normalizer.normalize(password, Normalizer.Form.NFC))) {
                result.isUnlockSuccess = true;
                result.normalization = Normalizer.Form.NFC;
            } else if (tryDecrypt(masterKey, Normalizer.normalize(password, Normalizer.Form.NFD))) {
                result.isUnlockSuccess = true;
                result.normalization = Normalizer.Form.NFD;
            } else if (tryDecrypt(masterKey, Normalizer.normalize(password, Normalizer.Form.NFKD))) {
                result.isUnlockSuccess = true;
                result.normalization = Normalizer.Form.NFKD;
            } else if (tryDecrypt(masterKey, Normalizer.normalize(password, Normalizer.Form.NFKC))) {
                result.isUnlockSuccess = true;
                result.normalization = Normalizer.Form.NFKC;
            }
            return result.isUnlockSuccess;
        }

        private boolean tryDecrypt(DeterministicKey masterKey, CharSequence password) {
            KeyCrypter crypter = checkNotNull(masterKey.getKeyCrypter());
            try {
                masterKey.decrypt(crypter, crypter.deriveKey(password));
                return true;
            } catch (KeyCrypterException e) {
                return false;
            }
        }

        protected void onPostExecute(Void aVoid) {
            passwordTestTask = null;
            if (Dialogs.dismissAllowingStateLoss(getFragmentManager(), PROCESSING_DIALOG_TAG)) return;

            if (error != null) {
                DialogBuilder.warn(getActivity(), R.string.error_generic)
                        .setMessage(error.getMessage())
                        .setPositiveButton(R.string.button_ok, null).create().show();
            } else {
                String yes = getString(R.string.yes);
                String no = getString(R.string.no);
                String fingerprint = "";
                try {
                    fingerprint = Hex.toHexString(Arrays.copyOf(
                            Sha256Hash.create(password.toString().getBytes("UTF-8")).getBytes(), 4));
                } catch (UnsupportedEncodingException e) { /* Should not happen */ }

                String message = getString(R.string.debugging_test_wallet_password_results,
                        result.isUnlockSuccess ? yes : no,
                        result.normalization != null ? result.normalization : "NONE",
                        result.isWhitespaceTrimmed ? yes : no, fingerprint);
                DialogBuilder.warn(getActivity(), R.string.debugging_test_wallet_password)
                        .setMessage(message)
                        .setPositiveButton(R.string.button_ok, null).create().show();
            }
        }
    }

    static class UnlockResult {
        Normalizer.Form normalization;
        boolean isUnlockSuccess = false;
        boolean isWhitespaceTrimmed = false;
    }
}
