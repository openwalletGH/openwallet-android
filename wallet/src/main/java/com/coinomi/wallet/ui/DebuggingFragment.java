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
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.wallet.Protos;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.encoders.Hex;

import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.Arrays;

import javax.annotation.Nullable;

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

        @Override
        protected void onPreExecute() {
            Dialogs.ProgressDialogFragment.show(getFragmentManager(),
                    getString(R.string.seed_working), PROCESSING_DIALOG_TAG);
        }

        @Override
        protected Void doInBackground(Void... params) {
            DeterministicKey masterKey = wallet.getMasterKey();
            tryDecrypt(masterKey, password, result);
            return null;
        }

        private void tryDecrypt(DeterministicKey masterKey, CharSequence password, UnlockResult result) {
            KeyCrypter crypter = checkNotNull(masterKey.getKeyCrypter());
            KeyParameter k = crypter.deriveKey(password);
            try {
                result.inputFingerprint = getFingerprint(password.toString().getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) { /* Should not happen */ }
            result.keyFingerprint = getFingerprint(k.getKey());
            if (crypter instanceof KeyCrypterScrypt) {
                result.scryptParams = ((KeyCrypterScrypt) crypter).getScryptParameters();
            }

            try {
                masterKey.decrypt(crypter, k);
                result.isUnlockSuccess = true;
            } catch (KeyCrypterException e) {
                result.isUnlockSuccess = false;
                result.error = e.getMessage();
            }
        }

        protected void onPostExecute(Void aVoid) {
            passwordTestTask = null;
            if (Dialogs.dismissAllowingStateLoss(getFragmentManager(), PROCESSING_DIALOG_TAG)) return;

            String yes = getString(R.string.yes);
            String no = getString(R.string.no);

            String message = getString(R.string.debugging_test_wallet_password_results,
                    result.isUnlockSuccess ? yes : no, result.inputFingerprint, result.keyFingerprint);
            if (result.scryptParams != null) {
                Protos.ScryptParameters sp = result.scryptParams;
                message += "\n\nScrypt:" +
                        "\nS = " + Hex.toHexString(sp.getSalt().toByteArray()) +
                        "\nN = " + sp.getN() +
                        "\nP = " + sp.getP() +
                        "\nR = " + sp.getR();
            }
            if (result.error != null) {
                message += "\n\n" + result.error;
            }
            DialogBuilder.warn(getActivity(), R.string.debugging_test_wallet_password)
                    .setMessage(message)
                    .setPositiveButton(R.string.button_ok, null).create().show();
        }
    }

    private String getFingerprint(byte[] b) {
        String inputFingerprint;
        inputFingerprint = Hex.toHexString(Arrays.copyOf(Sha256Hash.create(b).getBytes(), 4));
        return inputFingerprint;
    }

    static class UnlockResult {
        boolean isUnlockSuccess = false;
        String inputFingerprint;
        String keyFingerprint;
        @Nullable String error;
        @Nullable Protos.ScryptParameters scryptParams;
    }
}
