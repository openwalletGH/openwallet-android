package com.coinomi.wallet.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.adaptors.AccountListAdapter;
import com.coinomi.wallet.ui.widget.Amount;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.WeakHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.annotation.Nullable;

import static com.coinomi.wallet.ui.NavDrawerItemType.ITEM_COIN;

/**
 * Use the {@link OverviewFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class OverviewFragment extends Fragment{
    private static final Logger log = LoggerFactory.getLogger(OverviewFragment.class);

    private static final int WALLET_CHANGED = 0;
    private static final int UPDATE_VIEW = 1;

    private static final int AMOUNT_FULL_PRECISION = 8;
    private static final int AMOUNT_MEDIUM_PRECISION = 6;
    private static final int AMOUNT_SHORT_PRECISION = 4;
    private static final int AMOUNT_SHIFT = 0;

    private static final int ID_TRANSACTION_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;

    private final Handler handler = new MyHandler(this);

    private static class MyHandler extends WeakHandler<OverviewFragment> {
        public MyHandler(OverviewFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(OverviewFragment ref, Message msg) {
            switch (msg.what) {
                case WALLET_CHANGED:
                    ref.updateWallet();
                case UPDATE_VIEW:
                    ref.updateView();
                    break;
            }
        }
    }

    private Wallet wallet;
    private Value currentBalance;

    private boolean isFullAmount = false;
    private WalletApplication application;
    private Configuration config;

    private AccountListAdapter adapter;
    private LoaderManager loaderManager;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private Amount mainAmount;
    List<ExchangeRate> exchangeRates;

    private Listener listener;


    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param accountId of the account
     * @return A new instance of fragment InfoFragment.
     */
    public static OverviewFragment newInstance(String accountId) {
        OverviewFragment fragment = new OverviewFragment();
        Bundle args = new Bundle();
        args.putSerializable(Constants.ARG_ACCOUNT_ID, accountId);
        fragment.setArguments(args);
        return fragment;
    }

    public OverviewFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wallet = application.getWallet();
        if (wallet == null) {
            return;
        }

        setHasOptionsMenu(true);
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        exchangeRates = ExchangeRatesProvider.getRates(
                application.getApplicationContext(), config.getExchangeCurrencyCode());
        if (adapter != null) adapter.setExchangeRates(exchangeRates);
        //loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
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
        View view = inflater.inflate(R.layout.fragment_overview, container, false);

        if (wallet == null) {
            return view;
        }

        final ListView accountRows = (ListView) view.findViewById(R.id.account_rows);

        View header = inflater.inflate(R.layout.fragment_overview_header, null);
        // Initialize your header here.
        accountRows.addHeaderView(header, null, false);

        // Set a space in the end of the list
        View listFooter = new View(getActivity());
        listFooter.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin));
        accountRows.addFooterView(listFooter);

        if (wallet.getAllAccounts().size() > 0) {
            view.findViewById(R.id.welcome_message).setVisibility(View.GONE);
        }
        // Init list adapter
        adapter = new AccountListAdapter(inflater.getContext(), wallet);
        accountRows.setAdapter(adapter);
        adapter.setExchangeRates(exchangeRates);

        // Start AbstractTransactionDetailsActivity on click
        accountRows.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= accountRows.getHeaderViewsCount()) {
                    // Note the usage of getItemAtPosition() instead of adapter's getItem() because
                    // the latter does not take into account the header (which has position 0).
                    Object obj = parent.getItemAtPosition(position);

                    if (listener != null && obj != null && obj instanceof AbstractWallet) {
                        listener.onAccountSelected(((AbstractWallet) obj).getId());
                    } else {
                        Toast.makeText(getActivity(), getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        mainAmount = (Amount) view.findViewById(R.id.main_amount);
        //mainAmount.setSymbol(type.getSymbol());
        mainAmount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onLocalAmountClick();
            }
        });

        // Update the amount
        updateWallet();
        // TODO check if called by onResume
        updateView();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private final ThrottlingWalletChangeListener walletChangeListener = new ThrottlingWalletChangeListener() {

        @Override
        public void onThrottledWalletChanged() {
            handler.sendMessage(handler.obtainMessage(WALLET_CHANGED));
        }
    };

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mNavigationDrawerFragment != null && !mNavigationDrawerFragment.isDrawerOpen()) {
                //inflater.inflate(R.menu.global, menu);

        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
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
        wallet = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWallet();
        updateView();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void updateWallet() {
        if (wallet != null) {
            adapter.replace(wallet);
            if (currentBalance != null) {
                currentBalance = currentBalance.multiply(0);
            }
            for (WalletAccount w : wallet.getAllAccounts()) {
                ExchangeRate rate = ExchangeRatesProvider.getRate(application, w.getCoinType().getSymbol(), config.getExchangeCurrencyCode());
                if (rate == null) continue;
                if (currentBalance != null) {
                    currentBalance = currentBalance.add(rate.rate.convert(w.getBalance()));
                }
                else {
                    currentBalance = rate.rate.convert(w.getBalance());
                }
                log.info("Total: {}",
                        rate.rate.convert(w.getBalance()));
            }
        }
    }

    public void updateView() {
        if (currentBalance != null) {
            String newBalanceStr = GenericUtils.formatCoinValue(currentBalance.type, currentBalance,
                    isFullAmount ? AMOUNT_FULL_PRECISION : AMOUNT_SHORT_PRECISION, AMOUNT_SHIFT);
            mainAmount.setAmount(newBalanceStr);
            mainAmount.setSymbol(currentBalance.type.getSymbol());
        }
        exchangeRates = ExchangeRatesProvider.getRates(
                application.getApplicationContext(), config.getExchangeCurrencyCode());
        adapter.setExchangeRates(exchangeRates);
        adapter.notifyDataSetChanged();
    }

    public interface Listener {
        void onLocalAmountClick();
        void onAccountSelected(String accountId);
        void onSend();
        void onReceive();
    }
}
