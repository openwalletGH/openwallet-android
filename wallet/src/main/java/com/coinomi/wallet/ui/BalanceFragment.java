package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.WalletPocketConnectivity;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.Amount;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.WeakHandler;
import com.google.common.collect.Lists;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import javax.annotation.Nonnull;

/**
 * Use the {@link BalanceFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BalanceFragment extends Fragment implements LoaderCallbacks<List<Transaction>> {
    private static final Logger log = LoggerFactory.getLogger(BalanceFragment.class);

    private static final int WALLET_CHANGED = 0;
    private static final int UPDATE_VIEW = 1;

    private static final int AMOUNT_FULL_PRECISION = 8;
    private static final int AMOUNT_MEDIUM_PRECISION = 6;
    private static final int AMOUNT_SHORT_PRECISION = 4;
    private static final int AMOUNT_SHIFT = 0;

    private static final int ID_TRANSACTION_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;

    private final Handler handler = new MyHandler(this);

    private static class MyHandler extends WeakHandler<BalanceFragment> {
        public MyHandler(BalanceFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(BalanceFragment ref, Message msg) {
            switch (msg.what) {
                case WALLET_CHANGED:
                    ref.updateBalance();
                    ref.checkEmptyPocketMessage();
                    ref.updateConnectivityStatus();
                    break;
                case UPDATE_VIEW:
                    ref.updateView();
                    break;
            }
        }
    }

    private String accountId;
    private AbstractWallet pocket;
    private CoinType type;
    private Coin currentBalance;

    private boolean isFullAmount = false;
    private WalletApplication application;
    private ContentResolver resolver;
    private Configuration config;

    private TransactionsListAdapter adapter;
    private LoaderManager loaderManager;
    private View emptyPocketMessage;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private Amount mainAmount;
    private Amount localAmount;
    private TextView connectionLabel;

    private Listener listener;
    private final ContentObserver addressBookObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            adapter.clearLabelCache();
        }
    };

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param accountId of the account
     * @return A new instance of fragment InfoFragment.
     */
    public static BalanceFragment newInstance(String accountId) {
        BalanceFragment fragment = new BalanceFragment();
        Bundle args = new Bundle();
        args.putSerializable(Constants.ARG_ACCOUNT_ID, accountId);
        fragment.setArguments(args);
        return fragment;
    }

    public BalanceFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            accountId = getArguments().getString(Constants.ARG_ACCOUNT_ID);
        }
        //TODO
        pocket = (AbstractWallet) application.getAccount(accountId);
        if (pocket == null) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
            return;
        }
        type = pocket.getCoinType();
        setHasOptionsMenu(true);
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);

        loaderManager.initLoader(ID_TRANSACTION_LOADER, null, this);
        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onDestroy() {
        loaderManager.destroyLoader(ID_TRANSACTION_LOADER);
        loaderManager.destroyLoader(ID_RATE_LOADER);

        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_balance, container, false);

        final ListView transactionRows = (ListView) view.findViewById(R.id.transaction_rows);

        View header = inflater.inflate(R.layout.fragment_balance_header, null);
        // Initialize your header here.
        transactionRows.addHeaderView(header, null, false);

        // Set a space in the end of the list
        View listFooter = new View(getActivity());
        listFooter.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin));
        transactionRows.addFooterView(listFooter);

        emptyPocketMessage = header.findViewById(R.id.history_empty);
        // Hide empty message if have some transaction history
        if (pocket.getTransactions(false).size() > 0) {
            emptyPocketMessage.setVisibility(View.GONE);
        }

        // Init list adapter
        adapter = new TransactionsListAdapter(inflater.getContext(), pocket);
        adapter.setPrecision(AMOUNT_MEDIUM_PRECISION, 0);
        transactionRows.setAdapter(adapter);

        // Start TransactionDetailsActivity on click
        transactionRows.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= transactionRows.getHeaderViewsCount()) {
                    // Note the usage of getItemAtPosition() instead of adapter's getItem() because
                    // the latter does not take into account the header (which has position 0).
                    Object obj = parent.getItemAtPosition(position);

                    if (obj != null && obj instanceof Transaction) {
                        Intent intent = new Intent(getActivity(), TransactionDetailsActivity.class);
                        intent.putExtra(Constants.ARG_ACCOUNT_ID, accountId);
                        intent.putExtra(Constants.ARG_TRANSACTION_ID, ((Transaction) obj).getHashAsString());
                        startActivity(intent);
                    } else {
                        Toast.makeText(getActivity(), getString(R.string.get_tx_info_error), Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        mainAmount = (Amount) view.findViewById(R.id.main_amount);
        mainAmount.setSymbol(type.getSymbol());
        mainAmount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFullAmount = !isFullAmount;
                updateView();
            }
        });
        localAmount = (Amount) view.findViewById(R.id.amount_local);
        localAmount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onLocalAmountClick();
            }
        });

        connectionLabel = (TextView) view.findViewById(R.id.connection_label);

        exchangeRate = ExchangeRatesProvider.getRate(
                application.getApplicationContext(), type.getSymbol(), config.getExchangeCurrencyCode());
        // Update the amount
        updateBalance(pocket.getBalance());

        return view;
    }

    private void setupConnectivityStatus() {
        // Set connected for now...
        setConnectivityStatus(WalletPocketConnectivity.CONNECTED);
        // ... but check the status in some seconds
        handler.sendMessageDelayed(handler.obtainMessage(WALLET_CHANGED), 2000);
    }

    @Override
    public void onStart() {
        super.onStart();
        setupConnectivityStatus();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private void checkEmptyPocketMessage() {
        if (emptyPocketMessage.isShown()) {
            if (!pocket.isNew()) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        emptyPocketMessage.setVisibility(View.GONE);
                    }
                });
            }
        }
    }

    private void updateBalance() {
        updateBalance(pocket.getBalance());
    }

    private void updateBalance(final Value newBalance) {
        currentBalance = newBalance.toCoin();

        updateView();
    }

    private void updateConnectivityStatus() {
        setConnectivityStatus(pocket.getConnectivityStatus());
    }

    private void setConnectivityStatus(final WalletPocketConnectivity connectivity) {
        switch (connectivity) {
            case CONNECTED:
                connectionLabel.setVisibility(View.GONE);
                break;
            default:
            case DISCONNECTED:
                connectionLabel.setVisibility(View.VISIBLE);
        }
    }

    private final ThrottlingWalletChangeListener transactionChangeListener = new ThrottlingWalletChangeListener() {

        @Override
        public void onThrottledWalletChanged() {
            adapter.notifyDataSetChanged();
            handler.sendMessage(handler.obtainMessage(WALLET_CHANGED));
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            inflater.inflate(R.menu.balance, menu);
            // Disable sign/verify for coins that don't support it
            menu.findItem(R.id.action_sign_verify_message).setVisible(type.canSignVerifyMessages());
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
            resolver = activity.getContentResolver();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + Listener.class);
        }
        application = (WalletApplication) activity.getApplication();
        config = application.getConfiguration();
        loaderManager = getLoaderManager();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        application = null;
        pocket = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        resolver.registerContentObserver(AddressBookProvider.contentUri(
                getActivity().getPackageName(), type), true, addressBookObserver);

        pocket.addEventListener(transactionChangeListener, Threading.SAME_THREAD);

        checkEmptyPocketMessage();

        updateView();
    }

    @Override
    public void onPause() {
        pocket.removeEventListener(transactionChangeListener);
        transactionChangeListener.removeCallbacks();

        resolver.unregisterContentObserver(addressBookObserver);

        super.onPause();
    }

    @Override
    public Loader<List<Transaction>> onCreateLoader(int id, Bundle args) {
        return new TransactionsLoader(getActivity(), pocket);
    }

    @Override
    public void onLoadFinished(Loader<List<Transaction>> loader, final List<Transaction> transactions) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                adapter.replace(transactions);
            }
        });
    }

    @Override
    public void onLoaderReset(Loader<List<Transaction>> loader) { /* ignore */ }

    private static class TransactionsLoader extends AsyncTaskLoader<List<Transaction>> {
        private final AbstractWallet walletPocket;

        private TransactionsLoader(final Context context, @Nonnull final AbstractWallet walletPocket) {
            super(context);

            this.walletPocket = walletPocket;
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();

            walletPocket.addEventListener(transactionAddRemoveListener, Threading.SAME_THREAD);
            transactionAddRemoveListener.onWalletChanged(null); // trigger at least one reload

            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            walletPocket.removeEventListener(transactionAddRemoveListener);
            transactionAddRemoveListener.removeCallbacks();

            super.onStopLoading();
        }

        @Override
        public List<Transaction> loadInBackground() {
            final List<Transaction> filteredTransactions = Lists.newArrayList(walletPocket.getTransactions(true));

            Collections.sort(filteredTransactions, TRANSACTION_COMPARATOR);

            return filteredTransactions;
        }

        private final ThrottlingWalletChangeListener transactionAddRemoveListener = new ThrottlingWalletChangeListener() {
            @Override
            public void onThrottledWalletChanged() {
                try {
                    forceLoad();
                } catch (final RejectedExecutionException x) {
                    log.info("rejected execution: " + TransactionsLoader.this.toString());
                }
            }
        };

        private static final Comparator<Transaction> TRANSACTION_COMPARATOR = new Comparator<Transaction>() {
            @Override
            public int compare(final Transaction tx1, final Transaction tx2) {
                final boolean pending1 = tx1.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
                final boolean pending2 = tx2.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;

                if (pending1 != pending2)
                    return pending1 ? -1 : 1;

                // TODO use dates once implemented
//                final Date updateTime1 = tx1.getUpdateTime();
//                final long time1 = updateTime1 != null ? updateTime1.getTime() : 0;
//                final Date updateTime2 = tx2.getUpdateTime();
//                final long time2 = updateTime2 != null ? updateTime2.getTime() : 0;

                // If both not pending
                if (!pending1 && !pending2) {
                    final int time1 = tx1.getConfidence().getAppearedAtChainHeight();
                    final int time2 = tx2.getConfidence().getAppearedAtChainHeight();
                    if (time1 != time2)
                        return time1 > time2 ? -1 : 1;
                }

                return tx1.getHash().compareTo(tx2.getHash());
            }
        };
    }

    private ExchangeRate exchangeRate;
    private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
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
                exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
                handler.sendEmptyMessage(UPDATE_VIEW);
                if (log.isInfoEnabled()) {
                    try {
                        log.info("Got exchange rate: {}",
                                exchangeRate.rate.convert(type.oneCoin()).toFriendlyString());
                    } catch (Exception e) {
                        log.warn(e.getMessage());
                    }
                }
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };

    private void updateView() {
        if (currentBalance != null) {
            String newBalanceStr = GenericUtils.formatCoinValue(type, currentBalance,
                    isFullAmount ? AMOUNT_FULL_PRECISION : AMOUNT_SHORT_PRECISION, AMOUNT_SHIFT);
            mainAmount.setAmount(newBalanceStr);
        }

        if (currentBalance != null && exchangeRate != null && getView() != null) {
            try {
                Value fiatAmount = exchangeRate.rate.convert(type, currentBalance);
                localAmount.setAmount(GenericUtils.formatFiatValue(fiatAmount));
                localAmount.setSymbol(fiatAmount.type.getSymbol());
            } catch (Exception e) {
                // Should not happen
                localAmount.setAmount("");
                localAmount.setSymbol("ERROR");
            }
        }

        adapter.clearLabelCache();
    }

    public interface Listener {
        public void onLocalAmountClick();
    }
}
