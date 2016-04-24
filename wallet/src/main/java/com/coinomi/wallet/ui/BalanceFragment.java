package com.coinomi.wallet.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletConnectivityStatus;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.Amount;
import com.coinomi.wallet.ui.widget.SwipeRefreshLayout;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.WeakHandler;
import com.google.common.collect.Lists;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import javax.annotation.Nonnull;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemClick;

/**
 * Use the {@link BalanceFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BalanceFragment extends WalletFragment implements LoaderCallbacks<List<AbstractTransaction>> {
    private static final Logger log = LoggerFactory.getLogger(BalanceFragment.class);

    private static final int WALLET_CHANGED = 0;
    private static final int UPDATE_VIEW = 1;
    private static final int CLEAR_LABEL_CACHE = 2;

    private static final int AMOUNT_FULL_PRECISION = 8;
    private static final int AMOUNT_MEDIUM_PRECISION = 6;
    private static final int AMOUNT_SHORT_PRECISION = 4;
    private static final int AMOUNT_SHIFT = 0;

    private static final int ID_TRANSACTION_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;

    private String accountId;
    private WalletAccount pocket;
    private CoinType type;
    private Coin currentBalance;
    private ExchangeRate exchangeRate;

    private boolean isFullAmount = false;
    private WalletApplication application;
    private Configuration config;
    private final MyHandler handler = new MyHandler(this);
    private final ContentObserver addressBookObserver = new AddressBookObserver(handler);

            @Bind(R.id.transaction_rows) ListView transactionRows;
    @Bind(R.id.swipeContainer) SwipeRefreshLayout swipeContainer;
    @Bind(R.id.history_empty) View emptyPocketMessage;
    @Bind(R.id.account_balance) Amount accountBalance;
    @Bind(R.id.account_exchanged_balance) Amount accountExchangedBalance;
    @Bind(R.id.connection_label) TextView connectionLabel;
    private TransactionsListAdapter adapter;
    private Listener listener;
    private ContentResolver resolver;

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

        // The onCreateOptionsMenu is handled in com.coinomi.wallet.ui.AccountFragment
        setHasOptionsMenu(true);

        if (getArguments() != null) {
            accountId = getArguments().getString(Constants.ARG_ACCOUNT_ID);
        }
        //TODO
        pocket = application.getAccount(accountId);
        if (pocket == null) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
            return;
        }
        type = pocket.getCoinType();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_balance, container, false);
        addHeaderAndFooterToList(inflater, container, view);
        ButterKnife.bind(this, view);

        setupSwipeContainer();

        // TODO show empty message
        // Hide empty message if have some transaction history
        if (pocket.getTransactions().size() > 0) {
            emptyPocketMessage.setVisibility(View.GONE);
        }

        setupAdapter(inflater);
        accountBalance.setSymbol(type.getSymbol());
        exchangeRate = ExchangeRatesProvider.getRate(
                application.getApplicationContext(), type.getSymbol(), config.getExchangeCurrencyCode());
        // Update the amount
        updateBalance(pocket.getBalance());

        return view;
    }

    @Override
    public void onDestroyView() {
        adapter = null;
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    private void setupAdapter(LayoutInflater inflater) {
        // Init list adapter
        adapter = new TransactionsListAdapter(inflater.getContext(), (AbstractWallet) pocket);
        adapter.setPrecision(AMOUNT_MEDIUM_PRECISION, 0);
        transactionRows.setAdapter(adapter);
    }

    private void setupSwipeContainer() {
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (listener != null) {
                    listener.onRefresh();
                }
            }
        });
        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(
                R.color.progress_bar_color_1,
                R.color.progress_bar_color_2,
                R.color.progress_bar_color_3,
                R.color.progress_bar_color_4);
    }

    private void addHeaderAndFooterToList(LayoutInflater inflater, ViewGroup container, View view) {
        ListView list = ButterKnife.findById(view, R.id.transaction_rows);

        // Initialize header
        View header = inflater.inflate(R.layout.fragment_balance_header, null);
        list.addHeaderView(header, null, true);

        // Set a space in the end of the list
        View listFooter = new View(inflater.getContext());
        listFooter.setMinimumHeight(
                getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin));
        list.addFooterView(listFooter);
    }

    private void setupConnectivityStatus() {
        // Set connected for now...
        setConnectivityStatus(WalletConnectivityStatus.CONNECTED);
        // ... but check the status in some seconds
        handler.sendMessageDelayed(handler.obtainMessage(WALLET_CHANGED), 2000);
    }

    @OnItemClick(R.id.transaction_rows)
    public void onItemClick(int position) {
        if (position >= transactionRows.getHeaderViewsCount()) {
            // Note the usage of getItemAtPosition() instead of adapter's getItem() because
            // the latter does not take into account the header (which has position 0).
            Object obj = transactionRows.getItemAtPosition(position);

            if (obj != null && obj instanceof AbstractTransaction) {
                Intent intent = new Intent(getActivity(), TransactionDetailsActivity.class);
                intent.putExtra(Constants.ARG_ACCOUNT_ID, accountId);
                intent.putExtra(Constants.ARG_TRANSACTION_ID, ((AbstractTransaction) obj).getHashAsString());
                startActivity(intent);
            } else {
                Toast.makeText(getActivity(), getString(R.string.get_tx_info_error), Toast.LENGTH_LONG).show();
            }
        }
    }

    @OnClick(R.id.account_balance)
    public void onMainAmountClick() {
        isFullAmount = !isFullAmount;
        updateView();
    }

    @OnClick(R.id.account_exchanged_balance)
    public void onLocalAmountClick() {
        if (listener != null) listener.onLocalAmountClick();
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

    // TODO use the ListView feature that shows a view on empty list. Check exchange rates fragment
    @Deprecated
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

    private void setConnectivityStatus(final WalletConnectivityStatus connectivity) {
        switch (connectivity) {
            case CONNECTED:
            case LOADING:
                connectionLabel.setVisibility(View.GONE);
                break;
            case DISCONNECTED:
                connectionLabel.setVisibility(View.VISIBLE);
                break;
            default:
                throw new RuntimeException("Unknown connectivity status: " + connectivity);
        }
    }

    private final ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener() {

        @Override
        public void onThrottledWalletChanged() {
            if (adapter != null) adapter.notifyDataSetChanged();
            handler.sendMessage(handler.obtainMessage(WALLET_CHANGED));
        }
    };

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            listener = (Listener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + Listener.class);
        }
        resolver = context.getContentResolver();
        application = (WalletApplication) context.getApplicationContext();
        config = application.getConfiguration();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(ID_TRANSACTION_LOADER, null, this);
        getLoaderManager().initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onDetach() {
        getLoaderManager().destroyLoader(ID_TRANSACTION_LOADER);
        getLoaderManager().destroyLoader(ID_RATE_LOADER);
        listener = null;
        resolver = null;
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();

        resolver.registerContentObserver(AddressBookProvider.contentUri(
                getActivity().getPackageName(), type), true, addressBookObserver);

        pocket.addEventListener(walletChangeListener, Threading.SAME_THREAD);

        checkEmptyPocketMessage();

        updateView();
    }

    @Override
    public void onPause() {
        pocket.removeEventListener(walletChangeListener);
        walletChangeListener.removeCallbacks();

        resolver.unregisterContentObserver(addressBookObserver);

        super.onPause();
    }

    @Override
    public Loader<List<AbstractTransaction>> onCreateLoader(int id, Bundle args) {
        return new AbstractTransactionsLoader(getActivity(), pocket);
    }

    @Override
    public void onLoadFinished(Loader<List<AbstractTransaction>> loader, final List<AbstractTransaction> transactions) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (adapter != null) adapter.replace(transactions);
            }
        });
    }

    @Override
    public void onLoaderReset(Loader<List<AbstractTransaction>> loader) { /* ignore */ }

    @Override
    public WalletAccount getAccount() {
        return pocket;
    }

    private static class AbstractTransactionsLoader extends AsyncTaskLoader<List<AbstractTransaction>> {
        private final WalletAccount account;
        private final ThrottlingWalletChangeListener transactionAddRemoveListener;


        private AbstractTransactionsLoader(final Context context, @Nonnull final WalletAccount account) {
            super(context);

            this.account = account;
            this.transactionAddRemoveListener = new ThrottlingWalletChangeListener() {
                @Override
                public void onThrottledWalletChanged() {
                    try {
                        forceLoad();
                    } catch (final RejectedExecutionException x) {
                        log.info("rejected execution: " + AbstractTransactionsLoader.this.toString());
                    }
                }
            };
        }

        @Override
        protected void onStartLoading() {
            super.onStartLoading();

            account.addEventListener(transactionAddRemoveListener, Threading.SAME_THREAD);
            transactionAddRemoveListener.onWalletChanged(null); // trigger at least one reload

            forceLoad();
        }

        @Override
        protected void onStopLoading() {
            account.removeEventListener(transactionAddRemoveListener);
            transactionAddRemoveListener.removeCallbacks();

            super.onStopLoading();
        }

        @Override
        public List<AbstractTransaction> loadInBackground() {
            final List<AbstractTransaction> filteredAbstractTransactions = Lists.newArrayList(account.getTransactions().values());

            Collections.sort(filteredAbstractTransactions, TRANSACTION_COMPARATOR);

            return filteredAbstractTransactions;
        }

        private static final Comparator<AbstractTransaction> TRANSACTION_COMPARATOR = new Comparator<AbstractTransaction>() {
            @Override
            public int compare(final AbstractTransaction tx1, final AbstractTransaction tx2) {
                final boolean pending1 = tx1.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
                final boolean pending2 = tx2.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;

                if (pending1 != pending2)
                    return pending1 ? -1 : 1;

                // TODO use dates once implemented
//                final Date updateTime1 = tx1.getUpdateTime();
//                final long time1 = updateTime1 != null ? updateTime1.getTime() : 0;
//                final Date updateTime2 = tx2.getUpdateTime();
//                final long time2 = updateTime2 != null ? updateTime2.getTime() : 0;

                // If both not pending
                if (!pending1 && !pending2) {
                    final int time1 = tx1.getAppearedAtChainHeight();
                    final int time2 = tx2.getAppearedAtChainHeight();
                    if (time1 != time2)
                        return time1 > time2 ? -1 : 1;
                }

                return Arrays.equals(tx1.getHashBytes(),tx2.getHashBytes()) ? 1 : -1;
            }
        };
    }

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
        public void onLoaderReset(final Loader<Cursor> loader) { }
    };

    @Override
    public void updateView() {
        if (isRemoving() || isDetached()) return;

        if (currentBalance != null) {
            String newBalanceStr = GenericUtils.formatCoinValue(type, currentBalance,
                    isFullAmount ? AMOUNT_FULL_PRECISION : AMOUNT_SHORT_PRECISION, AMOUNT_SHIFT);
            accountBalance.setAmount(newBalanceStr);
        }

        if (currentBalance != null && exchangeRate != null && getView() != null) {
            try {
                Value fiatAmount = exchangeRate.rate.convert(type, currentBalance);
                accountExchangedBalance.setAmount(GenericUtils.formatFiatValue(fiatAmount));
                accountExchangedBalance.setSymbol(fiatAmount.type.getSymbol());
            } catch (Exception e) {
                // Should not happen
                accountExchangedBalance.setAmount("");
                accountExchangedBalance.setSymbol("ERROR");
            }
        }

        swipeContainer.setRefreshing(pocket.isLoading());

        if (adapter != null) adapter.clearLabelCache();
    }

    private void clearLabelCache() {
        if (adapter != null) adapter.clearLabelCache();
    }

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
                case CLEAR_LABEL_CACHE:
                    ref.clearLabelCache();
                    break;
            }
        }
    }

    static class AddressBookObserver extends ContentObserver {
        private final MyHandler handler;

        public AddressBookObserver(MyHandler handler) {
            super(handler);
            this.handler = handler;
        }

        @Override
        public void onChange(final boolean selfChange) {
            handler.sendEmptyMessage(CLEAR_LABEL_CACHE);
        }
    }

    public interface Listener {
        void onLocalAmountClick();
        void onRefresh();
    }
}
