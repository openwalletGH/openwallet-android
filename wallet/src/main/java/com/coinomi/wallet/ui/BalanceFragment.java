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
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.wallet.WalletPocketConnectivity;
import com.coinomi.core.wallet.WalletPocketEventListener;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.Amount;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
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

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Use the {@link BalanceFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BalanceFragment extends Fragment implements WalletPocketEventListener, LoaderCallbacks<List<Transaction>> {
    private static final Logger log = LoggerFactory.getLogger(BalanceFragment.class);

    private static final String COIN_TYPE = "coin_type";

    private static final int NEW_BALANCE = 0;
    private static final int PENDING = 1;
    private static final int CONNECTIVITY = 2;
    private static final int UPDATE_VIEW = 3;

    private static final int AMOUNT_FULL_PRECISION = 8;
    private static final int AMOUNT_MEDIUM_PRECISION = 6;
    private static final int AMOUNT_SHORT_PRECISION = 4;
    private static final int AMOUNT_SHIFT = 0;

    private static final int ID_TRANSACTION_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_BALANCE:
                    updateBalance((Coin) msg.obj);
                    break;
                case PENDING:
                    setPending((Coin) msg.obj);
                    break;
                case CONNECTIVITY:
                    setConnectivityStatus((WalletPocketConnectivity) msg.obj);
                    break;
                case UPDATE_VIEW:
                    updateView();
                    break;
            }
        }
    };

    private WalletApplication application;
    private ContentResolver resolver;
    private Configuration config;
    private WalletPocket pocket;
    private CoinType type;
    private TransactionsListAdapter adapter;

    private LoaderManager loaderManager;
    private View emptyPocketMessage;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private Amount mainAmount;
    private Amount localAmount;
    private TextView connectionLabel;
    private Coin currentBalance;
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
     * @param type of the coin
     * @return A new instance of fragment InfoFragment.
     */
    public static BalanceFragment newInstance(CoinType type) {
        BalanceFragment fragment = new BalanceFragment();
        Bundle args = new Bundle();
        args.putSerializable(COIN_TYPE, type);
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
            type = (CoinType) getArguments().getSerializable(COIN_TYPE);
        }

        checkNotNull(type);
        pocket = application.getWalletPocket(type);
        setHasOptionsMenu(true);
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
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
                        intent.putExtra(Constants.ARG_COIN_ID, type.getId());
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
        localAmount = (Amount) view.findViewById(R.id.amount_local);
        localAmount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onLocalAmountClick();
            }
        });

        connectionLabel = (TextView) view.findViewById(R.id.connection_label);

        // Subscribe and update the amount
        pocket.addEventListener(this);
        updateBalance(pocket.getBalance());
        setPending(pocket.getPendingBalance());

        return view;
    }

    private void setupConnectivityStatus() {
        // Set connected for now...
        setConnectivityStatus(WalletPocketConnectivity.CONNECTED);
        // ... but check the status in some seconds
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (pocket != null) {
                    setConnectivityStatus(pocket.getConnectivityStatus());
                }
            }
        }, 2000);
    }

    @Override
    public void onStart() {
        super.onStart();
        setupConnectivityStatus();
    }

    @Override
    public void onDestroyView() {
        pocket.removeEventListener(this);
        super.onDestroyView();
    }

    @Override
    public void onNewBalance(Coin newBalance, Coin pendingAmount) {
        handler.sendMessage(handler.obtainMessage(NEW_BALANCE, newBalance));
        handler.sendMessage(handler.obtainMessage(PENDING, pendingAmount));
    }

    // Handled by ThrottlingWalletChangeListener
    @Override
    public void onNewBlock(WalletPocket pocket) {
    }

    @Override
    public void onTransactionConfidenceChanged(WalletPocket pocket, Transaction tx) {
    }

    @Override
    public void onTransactionBroadcastFailure(WalletPocket pocket, Transaction tx) {
        if (listener != null) listener.onTransactionBroadcastFailure(pocket, tx);
    }

    @Override
    public void onTransactionBroadcastSuccess(WalletPocket pocket, Transaction tx) {
        if (listener != null) listener.onTransactionBroadcastSuccess(pocket, tx);
    }

    @Override
    public void onPocketChanged(WalletPocket pocket) {
        checkEmptyPocketMessage(pocket);
    }

    private void checkEmptyPocketMessage(WalletPocket pocket) {
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

    @Override
    public void onConnectivityStatus(WalletPocketConnectivity pocketConnectivity) {
        handler.sendMessage(handler.obtainMessage(CONNECTIVITY, pocketConnectivity));
    }

    private void updateBalance(Coin newBalance) {
        currentBalance = newBalance;

        updateView();
    }

    private void setPending(Coin pendingAmount) {
        if (pendingAmount.isZero()) {
            mainAmount.setAmountPending(null);
        } else {
            String pendingAmountStr = GenericUtils.formatCoinValue(type, pendingAmount,
                    AMOUNT_FULL_PRECISION, AMOUNT_SHIFT);
            mainAmount.setAmountPending(pendingAmountStr);
        }
    }

    private void setConnectivityStatus(WalletPocketConnectivity connectivity) {
        switch (connectivity) {
            case WORKING:
                // TODO support WORKING state
            case CONNECTED:
                connectionLabel.setVisibility(View.INVISIBLE);
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
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            inflater.inflate(R.menu.balance, menu);
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

        loaderManager.initLoader(ID_TRANSACTION_LOADER, null, this);
        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);

        pocket.addEventListener(transactionChangeListener, Threading.SAME_THREAD);

        checkEmptyPocketMessage(pocket);

        updateView();
    }

    @Override
    public void onPause() {
        pocket.removeEventListener(transactionChangeListener);
        transactionChangeListener.removeCallbacks();

        loaderManager.destroyLoader(ID_TRANSACTION_LOADER);
        loaderManager.destroyLoader(ID_RATE_LOADER);

        resolver.unregisterContentObserver(addressBookObserver);

        super.onPause();
    }

    @Override
    public Loader<List<Transaction>> onCreateLoader(int id, Bundle args) {
        return new TransactionsLoader(getActivity(), pocket);
    }

    @Override
    public void onLoadFinished(Loader<List<Transaction>> loader, List<Transaction> transactions) {
        adapter.replace(transactions);
    }

    @Override
    public void onLoaderReset(Loader<List<Transaction>> loader) { /* ignore */ }

    private static class TransactionsLoader extends AsyncTaskLoader<List<Transaction>> {
        private final WalletPocket walletPocket;

        private TransactionsLoader(final Context context, @Nonnull final WalletPocket walletPocket) {
            super(context);

            this.walletPocket = walletPocket;
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();

            walletPocket.addEventListener(transactionAddRemoveListener, Threading.SAME_THREAD);
            transactionAddRemoveListener.onPocketChanged(null); // trigger at least one reload

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
                                exchangeRate.rate.coinToFiat(type.getOneCoin()).toFriendlyString());
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
                    AMOUNT_FULL_PRECISION, AMOUNT_SHIFT);
            mainAmount.setAmount(newBalanceStr);
        }

        if (currentBalance != null && exchangeRate != null && getView() != null) {
            try {
                String fiatAmount = GenericUtils.formatFiatValue(
                        exchangeRate.rate.coinToFiat(currentBalance));
                localAmount.setAmount(fiatAmount);
                localAmount.setSymbol(exchangeRate.rate.fiat.currencyCode);
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

        public void onTransactionBroadcastSuccess(WalletPocket pocket, Transaction transaction);

        public void onTransactionBroadcastFailure(WalletPocket pocket, Transaction transaction);
    }
}
