package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.adaptors.AccountListAdapter;
import com.coinomi.wallet.ui.widget.Amount;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.UiUtils;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnItemClick;
import butterknife.OnItemLongClick;

/**
 * @author vbcs
 * @author John L. Jegutanis
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
    Map<String, ExchangeRate> exchangeRates;
    private NavigationDrawerFragment mNavigationDrawerFragment;

    @Bind(R.id.account_rows) ListView accountRows;
    @Bind(R.id.main_amount) Amount mainAmount;

    private Listener listener;

    public static OverviewFragment getInstance() {
        return new OverviewFragment();
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
        View header = inflater.inflate(R.layout.fragment_overview_header, null);
        accountRows = ButterKnife.findById(view, R.id.account_rows);
        accountRows.addHeaderView(header, null, false);
        ButterKnife.bind(this, view);

        if (wallet == null) {
            return view;
        }

        // Set a space in the end of the list
        View listFooter = new View(getActivity());
        listFooter.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin));
        accountRows.addFooterView(listFooter);

        // Init list adapter
        adapter = new AccountListAdapter(inflater.getContext(), wallet);
        accountRows.setAdapter(adapter);
        adapter.setExchangeRates(exchangeRates);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
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
            inflater.inflate(R.menu.overview, menu);
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            listener = (Listener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " + Listener.class);
        }
        application = (WalletApplication) context.getApplicationContext();
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

        // TODO add an event listener to the Wallet class
        for (WalletAccount account : wallet.getAllAccounts()) {
            account.addEventListener(walletChangeListener, Threading.SAME_THREAD);
        }

        updateWallet();
        updateView();
    }

    @Override
    public void onPause() {
        // TODO add an event listener to the Wallet class
        for (WalletAccount account : wallet.getAllAccounts()) {
            account.removeEventListener(walletChangeListener);
        }
        walletChangeListener.removeCallbacks();

        super.onPause();
    }

    @OnClick(R.id.main_amount)
    public void onMainAmountClick(View v) {
        if (listener != null) listener.onLocalAmountClick();
    }

    @OnItemClick(R.id.account_rows)
    public void onAmountClick(int position) {
        if (position >= accountRows.getHeaderViewsCount()) {
            // Note the usage of getItemAtPosition() instead of adapter's getItem() because
            // the latter does not take into account the header (which has position 0).
            Object obj = accountRows.getItemAtPosition(position);

            if (listener != null && obj != null && obj instanceof WalletAccount) {
                listener.onAccountSelected(((WalletAccount) obj).getId());
            } else {
                showGenericError();
            }
        }
    }

    @OnItemLongClick(R.id.account_rows)
    public boolean onAmountLongClick(int position) {
        if (position >= accountRows.getHeaderViewsCount()) {
            // Note the usage of getItemAtPosition() instead of adapter's getItem() because
            // the latter does not take into account the header (which has position 0).
            Object obj = accountRows.getItemAtPosition(position);
            Activity activity = getActivity();

            if (obj != null && obj instanceof WalletAccount && activity != null) {
                ActionMode actionMode = UiUtils.startAccountActionMode(
                        (WalletAccount) obj, activity, getFragmentManager());
                // Hack to dismiss this action mode when back is pressed
                if (activity instanceof WalletActivity) {
                    ((WalletActivity) activity).registerActionMode(actionMode);
                }

                return true;
            } else {
                showGenericError();
            }
        }
        return false;
    }

    private void showGenericError() {
        Toast.makeText(getActivity(), getString(R.string.error_generic), Toast.LENGTH_LONG).show();
    }

    public void updateWallet() {
        if (wallet != null) {
            adapter.replace(wallet);
            currentBalance = null;
            Map<String, ExchangeRate> rates = ExchangeRatesProvider.getRates(application, config.getExchangeCurrencyCode());
            for (WalletAccount w : wallet.getAllAccounts()) {
                ExchangeRate rate = rates.get(w.getCoinType().getSymbol());
                if (rate == null) {
                    log.info("Missing exchange rate for {}, skipping...", w.getCoinType().getName());
                    continue;
                }
                if (currentBalance != null) {
                    currentBalance = currentBalance.add(rate.rate.convert(w.getBalance()));
                }
                else {
                    currentBalance = rate.rate.convert(w.getBalance());
                }
            }
        }
    }

    public void updateView() {
        if (currentBalance != null) {
            String newBalanceStr = GenericUtils.formatFiatValue(currentBalance);

            mainAmount.setAmount(newBalanceStr);
            mainAmount.setSymbol(currentBalance.type.getSymbol());
        } else {
            mainAmount.setAmount("-.--");
            mainAmount.setSymbol("");
        }

        exchangeRates = ExchangeRatesProvider.getRates(
                application.getApplicationContext(), config.getExchangeCurrencyCode());
        adapter.setExchangeRates(exchangeRates);
        adapter.notifyDataSetChanged();
    }

    public interface Listener extends EditAccountFragment.Listener {
        void onLocalAmountClick();
        void onAccountSelected(String accountId);
    }
}
