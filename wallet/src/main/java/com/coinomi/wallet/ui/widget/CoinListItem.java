package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.ExchangeRatesProvider.ExchangeRate;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.WalletUtils;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * @author John L. Jegutanis
 */
public class CoinListItem extends LinearLayout implements Checkable {
    final View view;
    @Bind(R.id.item_icon) ImageView icon;
    @Bind(R.id.item_text) TextView title;
    @Bind(R.id.amount) Amount amount;

    private boolean isChecked = false;
    private CoinType type;

    public CoinListItem(Context context) {
        super(context);

        view = LayoutInflater.from(context).inflate(R.layout.coin_list_row, this, true);
        ButterKnife.bind(this, view);
    }

    public void setAccount(WalletAccount account) {
        this.type = account.getCoinType();
        title.setText(account.getDescriptionOrCoinName());
        icon.setImageResource(WalletUtils.getIconRes(account));
    }

    public void setCoin(CoinType type) {
        this.type = type;
        title.setText(type.getName());
        icon.setImageResource(WalletUtils.getIconRes(type));
    }

    public void setExchangeRate(ExchangeRate exchangeRate) {
        if (exchangeRate != null && type != null) {
            Value localAmount = exchangeRate.rate.convert(type.oneCoin());
            setFiatAmount(localAmount);
        } else {
            amount.setVisibility(View.GONE);
        }
    }

    public void setAmount(Value value) {
        amount.setAmount(GenericUtils.formatCoinValue(value.type, value, true));
        amount.setSymbol(value.type.getSymbol());
        amount.setVisibility(View.VISIBLE);
    }

    private void setFiatAmount(Value value) {
        amount.setAmount(GenericUtils.formatFiatValue(value));
        amount.setSymbol(value.type.getSymbol());
        amount.setVisibility(View.VISIBLE);
    }

    public void setAmountSingleLine(boolean isSingleLine) {
        amount.setSingleLine(isSingleLine);
    }

    @Override
    public void setChecked(boolean checked) {
        isChecked = checked;

        if (isChecked) {
            view.setBackgroundResource(R.color.primary_100);
        } else {
            view.setBackgroundResource(0);
        }
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }
}
