package com.coinomi.wallet.ui.adaptors;

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
import android.content.res.Resources;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractWallet;
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
 * @author Andreas Schildbach
 */
public class AccountListAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final AbstractWallet walletPocket;

    private final List<WalletAccount> accounts = new ArrayList<>();
    private final Resources res;
    private int precision = 0;
    private int shift = 0;
    private final HashMap<String, ExchangeRatesProvider.ExchangeRate> rates;
    private boolean showEmptyText = false;

    private final int colorSignificant;
    private final int colorLessSignificant;
    private final int colorInsignificant;
    private final int colorError;
    private final int colorCircularBuilding = Color.parseColor("#44ff44");
    private final String minedTitle;
    private final String fontIconMined;
    private final String sentToTitle;
    private final String fontIconSentTo;
    private final String receivedWithTitle;
    private final String receivedFromTitle;
    private final String fontIconReceivedWith;

    private final Map<AbstractAddress, String> labelCache = new HashMap<>();
    private final static Object CACHE_NULL_MARKER = "";

    private static final String CONFIDENCE_SYMBOL_DEAD = "\u271D"; // latin cross
    private static final String CONFIDENCE_SYMBOL_UNKNOWN = "?";

    public AccountListAdapter(final Context context, @Nonnull final Wallet wallet) {
        this.context = context;
        inflater = LayoutInflater.from(context);

        accounts.addAll(wallet.getAllAccounts());
        this.walletPocket = (AbstractWallet) wallet.getAllAccounts().get(0);

        res = context.getResources();
        colorSignificant = res.getColor(R.color.gray_87_text);
        colorLessSignificant = res.getColor(R.color.gray_54_sec_text_icons);
        colorInsignificant = res.getColor(R.color.gray_26_hint_text);
        colorError = res.getColor(R.color.fg_error);
        minedTitle = res.getString(R.string.wallet_transactions_coinbase);
        fontIconMined = res.getString(R.string.font_icon_mining);
        sentToTitle = res.getString(R.string.sent_to);
        fontIconSentTo = res.getString(R.string.font_icon_send_coins);
        receivedWithTitle = res.getString(R.string.received_with);
        receivedFromTitle = res.getString(R.string.received_from);
        fontIconReceivedWith = res.getString(R.string.font_icon_receive_coins);
        this.rates = new HashMap<String, ExchangeRatesProvider.ExchangeRate>();
    }

    public void setPrecision(final int precision, final int shift) {
        this.precision = precision;
        this.shift = shift;

        notifyDataSetChanged();
    }

    public void clear() {
        accounts.clear();

        notifyDataSetChanged();
    }

    /*public void replace(@Nonnull final org.bitcoinj.core.Transaction tx) {
        accounts.clear();
        accounts.add(new BitTransaction(tx));

        notifyDataSetChanged();
    }

    public void replace(@Nonnull final Transaction tx) {
        accounts.clear();
        accounts.add(new NxtTransaction(tx));

        notifyDataSetChanged();
    }*/

    public void replace(@Nonnull final Wallet wallet) {
        accounts.clear();
        accounts.addAll(wallet.getAllAccounts());

        // TODO
//        showEmptyText = true;

        notifyDataSetChanged();
    }

    @Override
    public boolean isEmpty() {
        return showEmptyText && super.isEmpty();
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

    public void setExchangeRates(@Nullable List<ExchangeRatesProvider.ExchangeRate> newRates) {
        if (newRates != null) {
            for (ExchangeRatesProvider.ExchangeRate rate : newRates) {
                    this.rates.put(rate.currencyCodeId, rate);
            }
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
        rowLabel.setText(account.getDescription());

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

        //final Amount rowOneRateValue = (Amount) row.findViewById(R.id.exchange_rate_row_rate_unit);
        final Amount rowRateValue = (Amount) row.findViewById(R.id.exchange_rate_row_rate);
        if (rate != null && account.getCoinType() != null) {
            Value localAmount = rate.rate.convert(account.getCoinType().oneCoin());
            GenericUtils.formatCoinValue(localAmount.type, localAmount, true);
            rowRateValue.setAmount(GenericUtils.formatFiatValue(localAmount, 2, 0));
            //rowOneRateValue.setAmount(GenericUtils.formatCoinValue(account.getCoinType(), account.getCoinType().oneCoin(), 4, 0));
            //rowOneRateValue.setSymbol(account.getCoinType().getSymbol());
            //rowOneRateValue.setVisibility(View.VISIBLE);
            rowRateValue.setSymbol(localAmount.type.getSymbol());
            rowRateValue.setVisibility(View.VISIBLE);
        } else {
            rowRateValue.setVisibility(View.GONE);
            //rowOneRateValue.setVisibility(View.GONE);
        }



    }

}
