package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.security.KeyChainException;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.crypto.KeyCrypterException;

import java.io.IOException;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * This fragment displays a busy message and makes the transaction in the background
 *
 */
public class SignTransactionFragment extends Fragment {
    private static final int PASSWORD_CONFIRMATION = 1;

    private Wallet wallet;
    private SendRequest request;

    @Nullable private String password;
    private SignTransactionActivity mListener;
    private MakeTransactionTask makeTransactionTask;

    public static SignTransactionFragment newInstance(Bundle args) {
        SignTransactionFragment fragment = new SignTransactionFragment();
        fragment.setArguments(args);
        return fragment;
    }
    public SignTransactionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wallet = mListener.getWalletApplication().getWallet();
        makeTransactionTask = null;
        if (getArguments() != null) {
            request = (SendRequest) getArguments().getSerializable(Constants.ARG_SEND_REQUEST);
        }

        if (wallet != null) {
            if (wallet.isEncrypted()) {
                startActivityForResult(new Intent(getActivity(), PasswordConfirmationActivity.class),
                        PASSWORD_CONFIRMATION);
            } else {
                maybeStartTask();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_make_transaction, container, false);
    }

    private void maybeStartTask() {
        if (makeTransactionTask == null) {
            makeTransactionTask = new MakeTransactionTask();
            makeTransactionTask.execute();
        }
    }

    private class MakeTransactionTask extends AsyncTask<Void, Void, Exception> {
        private Dialogs.ProgressDialogFragment busyDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            busyDialog = Dialogs.ProgressDialogFragment.newInstance(
                    getResources().getString(R.string.preparing_transaction));
            busyDialog.show(getFragmentManager(), null);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            Exception error = null;
            try {
                if (wallet.isEncrypted()) {
                    KeyCrypter crypter = checkNotNull(wallet.getKeyCrypter());
                    request.aesKey = crypter.deriveKey(password);
                }
                wallet.signRequest(request);
                wallet.broadcastTx(request);
            }
            catch (InsufficientMoneyException e) { error = e; }
            catch (NoSuchPocketException e) { error = e; }
            catch (KeyCrypterException e) { error = e; }
            catch (IOException e) { error = e; }

            return error;
        }

        protected void onPostExecute(Exception error) {
            busyDialog.dismiss();
            if (mListener != null) {
                mListener.onSignResult(error);
            }
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == PASSWORD_CONFIRMATION) {
            if (resultCode == Activity.RESULT_OK) {
                password = intent.getStringExtra(Constants.ARG_PASSWORD);
                maybeStartTask();
            }
        }
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (SignTransactionActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + SignTransactionActivity.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface Listener {
        public void onSignResult(@Nullable Exception error);
    }
}
