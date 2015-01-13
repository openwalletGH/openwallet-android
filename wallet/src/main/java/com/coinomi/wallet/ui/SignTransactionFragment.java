package com.coinomi.wallet.ui;

import android.app.Activity;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.SendRequest;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.TransactionAmountVisualizer;
import com.coinomi.wallet.util.Keyboard;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.KeyCrypter;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * This fragment displays a busy message and makes the transaction in the background
 *
 */
public class SignTransactionFragment extends Fragment {
    private static final int PASSWORD_CONFIRMATION = 1;

    // Loader IDs
    private static final int ID_RATE_LOADER = 0;

    @Nullable private String password;
    private SignTransactionActivity mListener;
    private MakeTransactionTask makeTransactionTask;
    private WalletApplication application;
    private Configuration config;
    private TransactionAmountVisualizer txVisualizer;
    private Address sendToAddress;
    private Coin sentAmount;
    private CoinType type;
    private SendRequest request;
    private LoaderManager loaderManager;

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
        config = application.getConfiguration();
        makeTransactionTask = null;

        Bundle args = getArguments();
        checkNotNull(args, "Must provide arguments");
        checkState(args.containsKey(Constants.ARG_COIN_ID), "Must provide a coin id");
        checkState(args.containsKey(Constants.ARG_SEND_TO_ADDRESS), "Must provide an address string");
        checkState(args.containsKey(Constants.ARG_SEND_AMOUNT), "Must provide an amount to send");

        try {
            type = CoinID.typeFromId(args.getString(Constants.ARG_COIN_ID));
            sendToAddress = new Address(type, args.getString(Constants.ARG_SEND_TO_ADDRESS));
            sentAmount = Coin.valueOf(args.getLong(Constants.ARG_SEND_AMOUNT));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_make_transaction, container, false);

        final EditText passwordView = (EditText) view.findViewById(R.id.password);
        if (application.getWallet() != null && application.getWallet().isEncrypted()) {
            passwordView.requestFocus();
        } else {
            passwordView.setVisibility(View.GONE);
        }

        WalletPocket pocket = application.getWalletPocket(type);
        boolean emptyWallet = sentAmount.equals(pocket.getBalance(false));

        // TODO handle in a task onCreate
        try {
            if (emptyWallet) {
                request = SendRequest.emptyWallet(sendToAddress);
            } else {
                request = SendRequest.to(sendToAddress, sentAmount);
            }
            request.signInputs = false;
            pocket.completeTx(request);
        } catch (Exception e) {
            if (mListener != null) {
                mListener.onSignResult(e);
            }
            return view;
        }

        txVisualizer = (TransactionAmountVisualizer) view.findViewById(R.id.transaction_amount_visualizer);
        txVisualizer.setTransaction(pocket, request.tx);

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
    public void onResume() {
        super.onResume();
        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onPause() {
        loaderManager.destroyLoader(ID_RATE_LOADER);
        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (SignTransactionActivity) activity;
            loaderManager = getLoaderManager();
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

    private final LoaderManager.LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            String localSymbol = config.getExchangeCurrencyCode();
            String coinSymbol = type.getSymbol();
            return new ExchangeRateLoader(getActivity(), config, localSymbol, coinSymbol);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                data.moveToFirst();
                final ExchangeRatesProvider.ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
                if (txVisualizer != null) txVisualizer.setExchangeRate(exchangeRate.rate);
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };
}
