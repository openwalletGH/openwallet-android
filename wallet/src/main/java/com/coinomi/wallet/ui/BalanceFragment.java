package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.wallet.WalletPocketEventListener;
import com.coinomi.core.wallet.exceptions.NoSuchPocketException;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.Amount;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionConfidence;
import com.google.bitcoin.crypto.KeyCrypterException;
import com.google.bitcoin.utils.Threading;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Use the {@link BalanceFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class BalanceFragment extends Fragment implements WalletPocketEventListener, LoaderManager.LoaderCallbacks<List<Transaction>> {
    private static final Logger log = LoggerFactory.getLogger(BalanceFragment.class);

    private static final String COIN_TYPE = "coin_type";
    private static final int NEW_BALANCE = 0;
    private static final int PENDING = 1;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_BALANCE:
                    updateBalance((Coin) msg.obj);
                    break;
                case PENDING:
                    setPending((Coin) msg.obj);
            }
        }
    };

    private WalletApplication application;
    private WalletPocket pocket;
    private CoinType type;
    private TransactionsListAdapter adapter;

    private LoaderManager loaderManager;
    private View emptyPocketMessage;
    private NavigationDrawerFragment mNavigationDrawerFragment;

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

        ListView transactionRows = (ListView) view.findViewById(R.id.transaction_rows);

        View header = inflater.inflate(R.layout.fragment_balance_header, null);
        // Initialize your header here.
        transactionRows.addHeaderView(header, null, false);

        // Set a space in the end of the list
        View listFooter = new View(getActivity());
        listFooter.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin));
        transactionRows.addFooterView(listFooter);

        emptyPocketMessage =  header.findViewById(R.id.history_empty);
        // Hide empty message if have some transaction history
        if (pocket.getTransactions(false).size() > 0) {
            emptyPocketMessage.setVisibility(View.GONE);
        }

        // Init list adapter
        adapter = new TransactionsListAdapter(inflater.getContext(), pocket);
        adapter.setPrecision(6, 0);
        transactionRows.setAdapter(adapter);

//// Just as a bonus - if you want to do something with your list items:
//        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                // You can just use listView instead of parent casted to ListView.
//                if (position >= ((ListView) parent).getHeaderViewsCount()) {
//                    // Note the usage of getItemAtPosition() instead of adapter's getItem() because
//                    // the latter does not take into account the header (which has position 0).
//                    Object obj = parent.getItemAtPosition(position);
//                    // Do something with your object.
//                }
//            }
//        });

        // Subscribe and update the amount
        pocket.addEventListener(this);
        updateBalance(pocket.getBalance(), view);
        setPending(pocket.getPendingBalance(), view);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        pocket.removeEventListener(this);
    }

    @Override
    public void onNewBalance(Coin newBalance, Coin pendingAmount) {
        handler.sendMessage(handler.obtainMessage(NEW_BALANCE, newBalance));
        handler.sendMessage(handler.obtainMessage(PENDING, pendingAmount));
    }

    @Override
    public void onTransactionConfidenceChanged(WalletPocket pocket, Transaction tx) { }

    @Override
    public void onPocketChanged(WalletPocket pocket) {
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

    private void updateBalance(Coin newBalance) {
        updateBalance(newBalance, getView());
    }

    private void updateBalance(Coin newBalance, View view) {
        if (view != null) {
            Amount mainAmount = (Amount) view.findViewById(R.id.main_amount);
            mainAmount.setAmount(newBalance);
            mainAmount.setSymbol(type.getSymbol());

//            Amount btcAmount = (Amount) view.findViewById(R.id.amount_btc);
//            btcAmount.setAmount(Coin.ZERO);
//            btcAmount.setSymbol(BitcoinMain.get().getSymbol());
//
//            Amount usdAmount = (Amount) view.findViewById(R.id.amount_usd);
//            usdAmount.setAmount(Coin.ZERO);
//            usdAmount.setSymbol(Usd.get().getSymbol());
//
//            Amount eurAmount = (Amount) view.findViewById(R.id.amount_eur);
//            eurAmount.setAmount(Coin.ZERO);
//            eurAmount.setSymbol(Eur.get().getSymbol());
//
//            Amount cnyAmount = (Amount) view.findViewById(R.id.amount_cny);
//            cnyAmount.setAmount(Coin.ZERO);
//            cnyAmount.setSymbol(Cny.get().getSymbol());
        }
    }

    private void setPending(Coin pendingAmount) {
        setPending(pendingAmount, getView());
    }

    private void setPending(Coin pendingAmount, View view) {
        if (view != null) {
            Amount mainAmount = (Amount) view.findViewById(R.id.main_amount);
            mainAmount.setAmountPending(pendingAmount);
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
        application = (WalletApplication) activity.getApplication();
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

        loaderManager.initLoader(0, null, this);

        pocket.addEventListener(transactionChangeListener, Threading.SAME_THREAD);
    }

    @Override
    public void onPause() {
        pocket.removeEventListener(transactionChangeListener);
        transactionChangeListener.removeCallbacks();

        loaderManager.destroyLoader(0);

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
        protected void onStartLoading()
        {
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
}
