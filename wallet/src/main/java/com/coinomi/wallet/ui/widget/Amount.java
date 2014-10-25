package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.coinomi.wallet.R;
import com.google.bitcoin.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class Amount extends RelativeLayout{
    private final TextView amount;
    private final TextView symbol;
    private final ProgressBar pendingProgress;
    private final TextView amountPending;

    boolean isBig = false;

    public Amount(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.amount, this, true);

        amount = (TextView) findViewById(R.id.amount);
        symbol = (TextView) findViewById(R.id.symbol);
        pendingProgress = (ProgressBar) findViewById(R.id.pending_progress);
        pendingProgress.setVisibility(GONE);
        amountPending = (TextView) findViewById(R.id.amount_pending);
        amountPending.setVisibility(GONE);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Amount, 0, 0);
        try {
            isBig = a.getBoolean(R.styleable.Amount_show_big, false);
        } finally {
            a.recycle();
        }

        try {
            if (isBig) {
                amount.setTextAppearance(context, R.style.AmountBig);
            }
        } catch (RuntimeException ignore) { }

        if (getRootView().isInEditMode()) {
            setAmount(Coin.valueOf(4200000010L));
            setAmountPending(Coin.valueOf(133700000L));
        }
    }

    public void setAmount(Coin amount) {
        this.amount.setText(amount.toPlainString());
    }

    public void setSymbol(String symbol) {
        this.symbol.setText(symbol);
    }

    public void setAmountPending(Coin newPending) {
        if (newPending.equals(Coin.ZERO)) {
            pendingProgress.setVisibility(GONE);
            amountPending.setVisibility(GONE);
            amountPending.setText("");
        } else {
            pendingProgress.setVisibility(VISIBLE);
            String text = (newPending.isPositive() ? " +" : " ") + newPending.toPlainString();
            amountPending.setText(text);
            amountPending.setVisibility(VISIBLE);
        }
    }
}
