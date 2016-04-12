package com.coinomi.wallet.ui.adaptors;

import android.content.Context;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.Amount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * @author John L. Jegutanis
 */
public class AccountListAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<WalletAccount> accounts = new ArrayList<>();
    private final HashMap<String, ExchangeRatesProvider.ExchangeRate> rates;

    public AccountListAdapter(final Context context, @Nonnull final Wallet wallet) {
        inflater = LayoutInflater.from(context);
        accounts.addAll(wallet.getAllAccounts());
        this.rates = new HashMap<>();
    }

    public void clear() {
        accounts.clear();

        notifyDataSetChanged();
    }

    public void replace(@Nonnull final Wallet wallet) {
        accounts.clear();
        accounts.addAll(wallet.getAllAccounts());

        notifyDataSetChanged();
    }

    @Override
    public boolean isEmpty() {
        return accounts.isEmpty();
    }

    @Override
    public int getCount() {
        return accounts.size();
    }

    @Override
    public WalletAccount getItem(final int position) {
        return accounts.get(position);
    }

    @Override
    public long getItemId(final int position) {
        if (position == accounts.size())
            return 0;

        return accounts.get(position).getId().hashCode();
    }

    public void setExchangeRates(@Nullable Map<String, ExchangeRatesProvider.ExchangeRate> newRates) {
        if (newRates != null) {
            this.rates.putAll(newRates);
        } else {
            rates.clear();
        }
        notifyDataSetChanged();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(final int position, View row, final ViewGroup parent) {
        if (row == null) row = inflater.inflate(R.layout.account_row, null);

        bindView(row, getItem(position));
        return row;
    }

    private void bindView(View row, WalletAccount account) {
        final ImageView icon = (ImageView) row.findViewById(R.id.account_icon);
        icon.setImageResource(Constants.COINS_ICONS.get(account.getCoinType()));

        final TextView rowLabel = (TextView) row.findViewById(R.id.account_description);
        rowLabel.setText(account.getDescriptionOrCoinName());

        final Amount rowValue = (Amount) row.findViewById(R.id.account_balance);
        rowValue.setAmount(GenericUtils.formatFiatValue(account.getBalance(), 4, 0));
        rowValue.setSymbol(account.getCoinType().getSymbol());

        ExchangeRatesProvider.ExchangeRate rate = rates.get(account.getCoinType().getSymbol());
        final Amount rowBalanceRateValue = (Amount) row.findViewById(R.id.account_balance_rate);
        if (rate != null && account.getCoinType() != null) {
            Value localAmount = rate.rate.convert(account.getBalance());
            GenericUtils.formatCoinValue(localAmount.type, localAmount,true);
            rowBalanceRateValue.setAmount(GenericUtils.formatFiatValue(localAmount, 2, 0));
            rowBalanceRateValue.setSymbol(localAmount.type.getSymbol());
            rowBalanceRateValue.setVisibility(View.VISIBLE);
        } else {
            rowBalanceRateValue.setVisibility(View.GONE);
        }

        final Amount rowRateValue = (Amount) row.findViewById(R.id.exchange_rate_row_rate);
        if (rate != null && account.getCoinType() != null) {
            Value localAmount = rate.rate.convert(account.getCoinType().oneCoin());
            GenericUtils.formatCoinValue(localAmount.type, localAmount, true);
            rowRateValue.setAmount(GenericUtils.formatFiatValue(localAmount, 2, 0));
            rowRateValue.setSymbol(localAmount.type.getSymbol());
            rowRateValue.setVisibility(View.VISIBLE);
        } else {
            rowRateValue.setVisibility(View.GONE);
        }
    }

}
