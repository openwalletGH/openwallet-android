package com.coinomi.wallet.ui;

import android.content.Context;
import android.content.Intent;
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

import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeHistoryProvider;
import com.coinomi.wallet.ExchangeHistoryProvider.ExchangeEntry;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.AddressView;
import com.coinomi.wallet.ui.widget.Amount;
import com.coinomi.wallet.util.Fonts;

import org.bitcoinj.core.Coin;

import javax.annotation.CheckForNull;


/**
 * @author John L. Jegutanis
 */
public final class ExchangeHistoryFragment extends ListFragment {
    private Context activity;
    private WalletApplication application;
    private Configuration config;
    private com.coinomi.core.wallet.Wallet wallet;
    private Uri contentUri;
    private LoaderManager loaderManager;

    private ExchangeEntryAdapter adapter;
    private String query = null;

    private Coin balance = null;
    @CheckForNull
    private String defaultCurrency = null;

    private static final int ID_EXCHANGES_LOADER = 0;
    private CoinType type;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);

        this.activity = context;
        this.application = (WalletApplication) context.getApplicationContext();
        this.wallet = application.getWallet();

        this.loaderManager = getLoaderManager();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        contentUri = ExchangeHistoryProvider.contentUri(application.getPackageName());

        adapter = new ExchangeEntryAdapter(activity);
        setListAdapter(adapter);

        loaderManager.initLoader(ID_EXCHANGES_LOADER, null, exchangesLoaderCallbacks);
    }

    @Override
    public void onDestroy() {
        loaderManager.destroyLoader(ID_EXCHANGES_LOADER);
        super.onDestroy();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_exchange_history, container, false);
    }

    @Override
    public void setEmptyText(final CharSequence text) {
        if (getView() != null) {
            final TextView emptyView = (TextView) getView().findViewById(android.R.id.empty);
            emptyView.setText(text);
        }
    }

    @Override
    public void onListItemClick(final ListView l, final View v, final int position, final long id) {
        final Cursor cursor = (Cursor) adapter.getItem(position);
        final ExchangeEntry entry = ExchangeHistoryProvider.getExchangeEntry(cursor);
        Intent intent = new Intent(getActivity(), TradeStatusActivity.class);
        intent.putExtra(Constants.ARG_EXCHANGE_ENTRY, entry);
        startActivity(intent);
    }

    private final LoaderCallbacks<Cursor> exchangesLoaderCallbacks = new LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            return new CursorLoader(activity, contentUri, null, null, null,
                    ExchangeHistoryProvider.KEY_ROWID + " DESC");
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            adapter.swapCursor(data);
            setEmptyText(getString(R.string.exchange_history_empty));
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {}
    };

    private final class ExchangeEntryAdapter extends ResourceCursorAdapter {
        private ExchangeEntryAdapter(final Context context) {
            super(context, R.layout.exchange_status_row, null, true);
        }

        @Override
        public void bindView(final View view, final Context context, final Cursor cursor) {
            final ExchangeEntry entry = ExchangeHistoryProvider.getExchangeEntry(cursor);
            final View okIcon = view.findViewById(R.id.exchange_status_ok_icon);
            final View errorIcon = view.findViewById(R.id.exchange_status_error_icon);
            Fonts.setTypeface(okIcon, Fonts.Font.COINOMI_FONT_ICONS);
            Fonts.setTypeface(errorIcon, Fonts.Font.COINOMI_FONT_ICONS);
            Fonts.setTypeface(view.findViewById(R.id.exchange_arrow), Fonts.Font.COINOMI_FONT_ICONS);
            final View progress = view.findViewById(R.id.exchange_status_progress);
            final TextView statusText = (TextView) view.findViewById(R.id.exchange_status_text);
            final View values = view.findViewById(R.id.exchange_values);
            final Amount deposit = (Amount) view.findViewById(R.id.exchange_deposit);
            final Amount withdraw = (Amount) view.findViewById(R.id.exchange_withdraw);
            final AddressView addressView = (AddressView) view.findViewById(R.id.withdraw_address);

            switch (entry.status) {
                case ExchangeEntry.STATUS_INITIAL:
                    okIcon.setVisibility(View.GONE);
                    errorIcon.setVisibility(View.GONE);
                    progress.setVisibility(View.VISIBLE);
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText(R.string.trade_status_waiting_deposit);
                    values.setVisibility(View.GONE);
                    addressView.setVisibility(View.GONE);
                    break;
                case ExchangeEntry.STATUS_PROCESSING:
                    okIcon.setVisibility(View.GONE);
                    errorIcon.setVisibility(View.GONE);
                    progress.setVisibility(View.VISIBLE);
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText(R.string.trade_status_waiting_trade);
                    values.setVisibility(View.GONE);
                    addressView.setVisibility(View.GONE);
                    break;
                case ExchangeEntry.STATUS_COMPLETE:
                    okIcon.setVisibility(View.VISIBLE);
                    errorIcon.setVisibility(View.GONE);
                    progress.setVisibility(View.GONE);
                    statusText.setVisibility(View.GONE);
                    values.setVisibility(View.VISIBLE);
                    deposit.setAmount(entry.depositAmount.toPlainString());
                    deposit.setSymbol(entry.depositAmount.type.getSymbol());
                    withdraw.setAmount(entry.withdrawAmount.toPlainString());
                    withdraw.setSymbol(entry.withdrawAmount.type.getSymbol());
                    addressView.setVisibility(View.VISIBLE);
                    addressView.setAddressAndLabel(entry.withdrawAddress);
                    break;
                case ExchangeEntry.STATUS_FAILED:
                    okIcon.setVisibility(View.GONE);
                    errorIcon.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.GONE);
                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText(R.string.trade_status_failed);
                    values.setVisibility(View.GONE);
                    addressView.setVisibility(View.GONE);
            }
        }
    }
}
