package com.coinomi.wallet.ui;


import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.HeaderWithFontIcon;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static com.coinomi.wallet.ExchangeRatesProvider.getRates;

/**
 * Fragment that restores a wallet
 */
public class SelectCoinsFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SelectCoinsFragment.class);
    private Listener listener;
    private String message;
    private boolean isMultipleChoice;
    private ListView coinList;
    private Button nextButton;

    private Configuration config;
    private Context context;
    private CoinExchangeListAdapter adapter;
    private LoaderManager loaderManager;

    private static final int ID_RATE_LOADER = 0;

    public static Fragment newInstance(Bundle args) {
        SelectCoinsFragment fragment = new SelectCoinsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static Fragment newInstance(String message, boolean isMultipleChoice, Bundle args) {
        args = args != null ? args : new Bundle();
        args.putString(Constants.ARG_MESSAGE, message);
        args.putBoolean(Constants.ARG_MULTIPLE_CHOICE, isMultipleChoice);
        return newInstance(args);
    }

    public SelectCoinsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            Bundle args = getArguments();
            isMultipleChoice = args.getBoolean(Constants.ARG_MULTIPLE_CHOICE);
            message = args.getString(Constants.ARG_MESSAGE);
        }

        adapter = new CoinExchangeListAdapter(context, Constants.SUPPORTED_COINS);

        String localSymbol = config.getExchangeCurrencyCode();
        adapter.setExchangeRates(getRates(getActivity(), localSymbol));
        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onDestroy() {
        loaderManager.destroyLoader(ID_RATE_LOADER);

        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_select_coins_list, container, false);

        nextButton = (Button) view.findViewById(R.id.button_next);
        if (isMultipleChoice) {
            nextButton.setEnabled(false);
            nextButton.setOnClickListener(getNextOnClickListener());
        } else {
            nextButton.setVisibility(View.GONE);
        }

        coinList = (ListView) view.findViewById(R.id.coins_list);
        // Set header if needed
        if (message != null) {
            HeaderWithFontIcon header = new HeaderWithFontIcon(context);
            header.setFontIcon(R.string.font_icon_coins);
            header.setMessage(R.string.select_coins);
            coinList.addHeaderView(header, null, false);
        } else {
            View topPaddingView = new View(context);
            topPaddingView.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.half_standard_margin));
            coinList.addHeaderView(topPaddingView, null, false);
        }
        if (isMultipleChoice) coinList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        coinList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                update(position);
            }
        });
        coinList.setAdapter(adapter);

        return view;
    }

    private View.OnClickListener getNextOnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> ids = new ArrayList<String>();
                SparseBooleanArray selected = coinList.getCheckedItemPositions();
                for (int i = 0; i < selected.size(); i++) {
                    if (selected.valueAt(i)) {
                        CoinType type = getCoinType(selected.keyAt(i));
                        ids.add(type.getId());
                    }
                }
                selectCoins(ids);
            }
        };
    }

    private void update(int currentSelection) {
        if (isMultipleChoice) {
            boolean isCoinSelected = false;
            SparseBooleanArray selected = coinList.getCheckedItemPositions();
            for (int i = 0; i < selected.size(); i++) {
                if (selected.valueAt(i)) {
                    isCoinSelected = true;
                    break;
                }
            }
            nextButton.setEnabled(isCoinSelected);
        } else if (currentSelection >= 0) {
            CoinType type = getCoinType(currentSelection);
            selectCoins(Lists.newArrayList(type.getId()));
        }
    }

    private CoinType getCoinType(int position) {
        return (CoinType) coinList.getItemAtPosition(position);
    }

    private void selectCoins(ArrayList<String> ids) {
        if (listener != null) {
            Bundle args = getArguments() == null ? new Bundle() : getArguments();
            args.putStringArrayList(Constants.ARG_MULTIPLE_COIN_IDS, ids);
            listener.onCoinSelection(args);
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            listener = (Listener) context;
            this.context = context;
            WalletApplication application = (WalletApplication) context.getApplicationContext();
            config = application.getConfiguration();
            loaderManager = getLoaderManager();
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " + Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface Listener {
        void onCoinSelection(Bundle args);
    }

    private final LoaderManager.LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            String localCurrency = config.getExchangeCurrencyCode();
            return new ExchangeRateLoader(getActivity(), config, localCurrency);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                ImmutableMap.Builder<String, ExchangeRate> builder = ImmutableMap.builder();

                data.moveToFirst();
                do {
                    ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
                    builder.put(exchangeRate.currencyCodeId, exchangeRate);
                } while (data.moveToNext());

                adapter.setExchangeRates(builder.build());
            }
        }

        @Override public void onLoaderReset(final Loader<Cursor> loader) { }
    };
}