package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.LayoutUtils;
import com.coinomi.wallet.util.Qr;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.wallet.DeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class ShowSeedFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(ShowSeedFragment.class);

    private static final int UPDATE_VIEW = 0;

    private int maxQrSize;
    private View seedLayout;
    private View seedEncryptedLayout;
    private TextView seedView;
    private View seedPasswordProtectedView;
    private ImageView qrView;
    private Listener listener;
    private LoadSeedTask decryptSeedTask;

    private Wallet wallet;
    private CharSequence password;

    private final Handler handler = new MyHandler(this);
    private static class MyHandler extends WeakHandler<ShowSeedFragment> {
        public MyHandler(ShowSeedFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(ShowSeedFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_VIEW:
                    ref.updateView();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        maxQrSize = LayoutUtils.calculateMaxQrCodeSize(getResources());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_show_seed, container, false);

        seedLayout = view.findViewById(R.id.show_seed_layout);
        seedEncryptedLayout = view.findViewById(R.id.seed_encrypted_layout);
        seedEncryptedLayout.setVisibility(View.GONE);
        // Hide layout as maybe we have to show the password dialog
        seedLayout.setVisibility(View.GONE);
        seedView = (TextView) view.findViewById(R.id.seed);
        seedPasswordProtectedView = view.findViewById(R.id.seed_password_protected);
        Fonts.setTypeface(view.findViewById(R.id.seed_password_protected_lock), Fonts.Font.COINOMI_FONT_ICONS);
        qrView = (ImageView) view.findViewById(R.id.qr_code_seed);

        TextView lockIcon = (TextView) view.findViewById(R.id.lock_icon);
        Fonts.setTypeface(lockIcon, Fonts.Font.COINOMI_FONT_ICONS);
        lockIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passwordDialog.show(getFragmentManager(), null);
            }
        });

        updateView();

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
            WalletApplication application = (WalletApplication) activity.getApplication();
            wallet = application.getWallet();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + ShowSeedFragment.Listener.class.getCanonicalName());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    private void updateView() {
        if (wallet != null) {
            if (wallet.getSeed() == null) {
                if (listener != null) listener.onSeedNotAvailable();
            } else if (wallet.getSeed().isEncrypted()) {
                seedEncryptedLayout.setVisibility(View.VISIBLE);
                if (password == null) {
                    passwordDialog.show(getFragmentManager(), null);
                } else {
                    maybeStartDecryptTask();
                }
            } else {
                seedEncryptedLayout.setVisibility(View.GONE);
                maybeStartDecryptTask();
            }
        }
    }

    DialogFragment passwordDialog = new UnlockWalletDialog() {
        @Override
        public void onPassword(CharSequence password) {
            ShowSeedFragment.this.password = password;
            handler.sendEmptyMessage(UPDATE_VIEW);
        }
        @Override public void onCancel() { }
    };

    private void maybeStartDecryptTask() {
        if (decryptSeedTask == null) {
            decryptSeedTask = new LoadSeedTask();
            decryptSeedTask.execute();
        }
    }

    private class LoadSeedTask extends AsyncTask<Void, Void, Void> {
        private Dialogs.ProgressDialogFragment busyDialog;
        Bitmap qrCodeBitmap;
        private String seedString;
        private boolean isSeedPasswordProtected;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            busyDialog = Dialogs.ProgressDialogFragment.newInstance(
                    getResources().getString(R.string.seed_working));
            busyDialog.show(getFragmentManager(), null);
        }

        @Override
        protected Void doInBackground(Void... params) {
            KeyParameter aesKey = null;
            DeterministicKey masterKey = null;
            DeterministicSeed seed = wallet.getSeed();
            if (seed != null) {
                try {
                    if (wallet.getKeyCrypter() != null) {
                        KeyCrypter crypter = wallet.getKeyCrypter();
                        aesKey = crypter.deriveKey(password);
                        seed = wallet.getSeed().decrypt(crypter, password.toString(), aesKey);
                        masterKey = wallet.getMasterKey().decrypt(crypter, aesKey);
                    } else {
                        masterKey = wallet.getMasterKey();
                    }
                    checkState(!seed.isEncrypted());
                    checkState(!masterKey.isEncrypted());
                    // Use empty password to check if the seed is password protected
                    seed = new DeterministicSeed(seed.getMnemonicCode(), null, "", 0);
                } catch (Exception e) {
                    log.warn("Failed recovering seed.");
                }
            }

            if (seed != null && masterKey != null) {
                seedString = Wallet.mnemonicToString(seed.getMnemonicCode());
                DeterministicKey testMasterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
                isSeedPasswordProtected = !masterKey.getPrivKey().equals(testMasterKey.getPrivKey());
                qrCodeBitmap = Qr.bitmap(seedString, maxQrSize);
            }

            return null;
        }

        protected void onPostExecute(Void aVoid) {
            decryptSeedTask = null;
            password = null;
            busyDialog.dismissAllowingStateLoss();
            if (seedString != null) {
                seedLayout.setVisibility(View.VISIBLE);
                seedEncryptedLayout.setVisibility(View.GONE);
                seedView.setText(seedString);
                qrView.setImageBitmap(qrCodeBitmap);
                if (isSeedPasswordProtected) {
                    seedPasswordProtectedView.setVisibility(View.VISIBLE);
                } else {
                    seedPasswordProtectedView.setVisibility(View.GONE);
                }
            } else {
                seedEncryptedLayout.setVisibility(View.VISIBLE);
                DialogBuilder.warn(getActivity(), R.string.unlocking_wallet_error_title)
                        .setMessage(R.string.unlocking_wallet_error_detail)
                        .setNegativeButton(R.string.button_cancel, null)
                        .setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                passwordDialog.show(getFragmentManager(), null);
                            }
                        })
                        .create().show();
            }
        }
    }

    public interface Listener {
        public void onSeedNotAvailable();
    }
}
