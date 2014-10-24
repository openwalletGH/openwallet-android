package com.coinomi.wallet.ui;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.SendOutput;
import com.coinomi.wallet.util.Keyboard;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.crypto.KeyCrypter;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * This fragment displays a busy message and makes the transaction in the background
 *
 */
public class SignTransactionFragment extends Fragment {
    private static final int PASSWORD_CONFIRMATION = 1;

    private SendRequest request;

    @Nullable private String password;
    private SignTransactionActivity mListener;
    private MakeTransactionTask makeTransactionTask;
    private WalletApplication application;

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
        application = mListener.getWalletApplication();
        makeTransactionTask = null;
        if (getArguments() != null) {
            request = (SendRequest) getArguments().getSerializable(Constants.ARG_SEND_REQUEST);
        }
        checkState(request != null, "Must provide a " + SendRequest.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_make_transaction, container, false);

        final EditText passwordView = (EditText) view.findViewById(R.id.password);
        if (application.getWallet() != null && application.getWallet().isEncrypted()) {
            Keyboard.focusAndShowKeyboard(passwordView, getActivity());
        } else {
            passwordView.setVisibility(View.GONE);
        }

        SendOutput output = (SendOutput) view.findViewById(R.id.transaction_output);
        SendOutput fee = (SendOutput) view.findViewById(R.id.transaction_fee);
        fee.setVisibility(View.GONE);

        WalletPocket pocket = application.getWalletPocket(request.type);
        String symbol = request.type.getSymbol();
        checkState(request.tx.getOutputs().size() == 1, "Only one output is supported at the moment.");
        for (TransactionOutput txo : request.tx.getOutputs()) {
            output.setAmount(txo.getValue());
            output.setSymbol(symbol);
            output.setAddress(txo.getScriptPubKey().getToAddress(request.type));
        }
        // TODO handle in a task onCreate
        request.signInputs = false;
        try {
            pocket.completeTx(request);
        } catch (InsufficientMoneyException e) {
            if (mListener != null) {
                mListener.onSignResult(e);
            }
            return view;
        }

        fee.setVisibility(View.VISIBLE);
        fee.setAmount(request.tx.getFee());
        fee.setSymbol(symbol);

        view.findViewById(R.id.button_confirm).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (passwordView.isShown()) {
                    Keyboard.hideKeyboard(getActivity());
                    password = passwordView.getText().toString();
                }
                maybeStartTask();
            }
        });

        return view;
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
            Wallet wallet = application.getWallet();
            if (wallet == null) return new NoSuchPocketException("No wallet found.");
            Exception error = null;
            try {
                if (wallet.isEncrypted()) {
                    KeyCrypter crypter = checkNotNull(wallet.getKeyCrypter());
                    request.aesKey = crypter.deriveKey(password);
                }
                request.signInputs = true;
                wallet.completeAndSignTx(request);
                wallet.broadcastTx(request);
            }
            catch (Exception e) { error = e; }

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
