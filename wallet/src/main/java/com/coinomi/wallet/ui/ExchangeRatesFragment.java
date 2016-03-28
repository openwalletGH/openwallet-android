package com.coinomi.wallet.ui;

/*
 * Copyright 2011-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.ResourceCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.Amount;
import com.coinomi.wallet.util.WalletUtils;

import org.bitcoinj.core.Coin;

import javax.annotation.CheckForNull;


/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public final class ExchangeRatesFragment extends ListFragment implements OnSharedPreferenceChangeListener {
    private Context context;
    private WalletApplication application;
    private Configuration config;
    private com.coinomi.core.wallet.Wallet wallet;
    private Uri contentUri;
    private LoaderManager loaderManager;

    private ExchangeRatesAdapter adapter;
    private String query = null;

    private Coin balance = null;
    @CheckForNull
    private String defaultCurrency = null;

    private static final int ID_BALANCE_LOADER = 0;
    private static final int ID_RATE_LOADER = 1;
    private static final int ID_BLOCKCHAIN_STATE_LOADER = 2;
    private CoinType type;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);

        this.context = context;
        this.application = (WalletApplication) context.getApplicationContext();
        this.config = application.getConfiguration();
        this.wallet = application.getWallet();

        this.loaderManager = getLoaderManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null && getArguments().containsKey(Constants.ARG_COIN_ID)) {
            type = CoinID.typeFromId(getArguments().getString(Constants.ARG_COIN_ID));
        } else {
            type = BitcoinMain.get();
        }
        contentUri = ExchangeRatesProvider.contentUriToLocal(context.getPackageName(),
                type.getSymbol(), false);

        defaultCurrency = config.getExchangeCurrencyCode();
        config.registerOnSharedPreferenceChangeListener(this);

        adapter = new ExchangeRatesAdapter(context);
        setListAdapter(adapter);

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exchange_rates, container, false);
    }

    @Override
    public void setEmptyText(final CharSequence text) {
        final TextView emptyView = (TextView) getView().findViewById(android.R.id.empty);
        emptyView.setText(text);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getListView().setFastScrollEnabled(true);
        setEmptyText(getString(R.string.exchange_rates_loading));
    }

    @Override
    public void onResume() {
        super.onResume();

//        loaderManager.initLoader(ID_BALANCE_LOADER, null, balanceLoaderCallbacks);
//        loaderManager.initLoader(ID_BLOCKCHAIN_STATE_LOADER, null, blockchainStateLoaderCallbacks);

        updateView();
    }

    @Override
    public void onPause() {
//        loaderManager.destroyLoader(ID_BALANCE_LOADER);
//        loaderManager.destroyLoader(ID_BLOCKCHAIN_STATE_LOADER);

        super.onPause();
    }

    @Override
    public void onDestroy() {
        config.unregisterOnSharedPreferenceChangeListener(this);

        loaderManager.destroyLoader(ID_RATE_LOADER);

        super.onDestroy();
    }
//
//    @Override
//    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
//        inflater.inflate(R.menu.exchange_rates_fragment_options, menu);
//
//        final SearchView searchView = (SearchView) menu.findItem(R.id.exchange_rates_options_search).getActionView();
//        searchView.setOnQueryTextListener(new OnQueryTextListener() {
//            @Override
//            public boolean onQueryTextChange(final String newText) {
//                query = newText.trim();
//                if (query.isEmpty())
//                    query = null;
//
//                getLoaderManager().restartLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
//
//                return true;
//            }
//
//            @Override
//            public boolean onQueryTextSubmit(final String query) {
//                searchView.clearFocus();
//
//                return true;
//            }
//        });
//
//        super.onCreateOptionsMenu(menu, inflater);
//    }
//
    @Override
    public void onListItemClick(final ListView l, final View v, final int position, final long id) {
        final Cursor cursor = (Cursor) adapter.getItem(position);
        final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);

        defaultCurrency = exchangeRate.currencyCodeId;
        config.setExchangeCurrencyCode(defaultCurrency);

        Toast.makeText(getActivity(), getString(R.string.set_local_currency, defaultCurrency),
                Toast.LENGTH_SHORT).show();

        getActivity().finish();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (Configuration.PREFS_KEY_EXCHANGE_CURRENCY.equals(key)) {
            defaultCurrency = config.getExchangeCurrencyCode();

            updateView();
        }
    }

    private void updateView() {
//        balance = application.getWallet().getBalance(BalanceType.ESTIMATED);

        if (adapter != null && type != null) {
            adapter.setRateBase(type.getOneCoin());
        }
    }

    private final LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            if (query == null) {
                return new CursorLoader(context, contentUri, null, null, null, null);
            } else {
                return new CursorLoader(context, contentUri, null, null, null, null);
            }
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            final Cursor oldCursor = adapter.swapCursor(data);

            if (data != null && oldCursor == null && defaultCurrency != null) {
                final int defaultCurrencyPosition = findCurrencyCode(data, defaultCurrency);
                if (defaultCurrencyPosition >= 0)
                    getListView().setSelection(defaultCurrencyPosition); // scroll to selection
            }

            setEmptyText(getString(query != null ? R.string.exchange_rates_empty_search
                    : R.string.exchange_rates_load_error));
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }

        private int findCurrencyCode(final Cursor cursor, final String currencyCode) {
            final int currencyCodeColumn = cursor.getColumnIndexOrThrow(ExchangeRatesProvider.KEY_CURRENCY_ID);

            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                if (cursor.getString(currencyCodeColumn).equals(currencyCode))
                    return cursor.getPosition();
            }

            return -1;
        }
    };

    private final class ExchangeRatesAdapter extends ResourceCursorAdapter {
        private Coin rateBase = Coin.COIN;

        private ExchangeRatesAdapter(final Context context) {
            super(context, R.layout.exchange_rate_row, null, true);
        }

        public void setRateBase(final Coin rateBase) {
            this.rateBase = rateBase;

            notifyDataSetChanged();
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor) {
            final ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(cursor);
            final boolean isDefaultCurrency = exchangeRate.currencyCodeId.equals(defaultCurrency);

            view.setBackgroundResource(isDefaultCurrency ? R.color.bg_list_selected : R.color.bg_list);

            final TextView currencyCodeView = (TextView) view.findViewById(R.id.exchange_rate_row_currency_code);
            currencyCodeView.setText(exchangeRate.currencyCodeId);

            final TextView currencyNameView = (TextView) view.findViewById(R.id.exchange_rate_row_currency_name);
            String currencyName = WalletUtils.getCurrencyName(exchangeRate.currencyCodeId);
            if (currencyName != null) {
                currencyNameView.setText(currencyName);
                currencyNameView.setVisibility(View.VISIBLE);
            } else {
                currencyNameView.setText(null);
                currencyNameView.setVisibility(View.INVISIBLE);
            }

            final Amount rateAmountUnitView = (Amount) view.findViewById(R.id.exchange_rate_row_rate_unit);
            rateAmountUnitView.setAmount(GenericUtils.formatCoinValue(type, rateBase, true));
            rateAmountUnitView.setSymbol(type.getSymbol());

            final Amount rateAmountView = (Amount) view.findViewById(R.id.exchange_rate_row_rate);
            Value fiatAmount = exchangeRate.rate.convert(type, rateBase);
            rateAmountView.setAmount(GenericUtils.formatFiatValue(fiatAmount));
            rateAmountView.setSymbol(fiatAmount.type.getSymbol());
        }
    }
}
