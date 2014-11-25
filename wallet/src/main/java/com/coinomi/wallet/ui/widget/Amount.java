package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.coinomi.core.wallet.WalletPocketConnectivity;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;

import javax.annotation.Nullable;

/**
 * @author Giannis Dzegoutanis
 */
public class Amount extends RelativeLayout {
    private final FontFitTextView amountText;
    private final TextView symbol;
//    private final TextView amountPending;
    boolean isBig = false;

    public Amount(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.amount, this, true);

        symbol = (TextView) findViewById(R.id.symbol);
//        amountPending = (TextView) findViewById(R.id.amount_pending);
//        amountPending.setVisibility(GONE);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Amount, 0, 0);
        try {
            isBig = a.getBoolean(R.styleable.Amount_show_big, false);
        } finally {
            a.recycle();
        }

        amountText = (FontFitTextView) findViewById(R.id.amount_text);

        if (isBig && !getRootView().isInEditMode()) {
            amountText.setTextAppearance(context, R.style.AmountBig);
        }

        amountText.setMaxTextSize(amountText.getTextSize());

        if (getRootView().isInEditMode()) {
            amountText.setText("3.14159265");
        }
    }

    public void setAmount(String amount) {
        amountText.setText(amount);
    }

    public void setSymbol(String symbol) {
        this.symbol.setText(symbol);
    }

    public void setAmountPending(@Nullable String pendingAmount) {
//        if (pendingAmount == null) {
//            amountPending.setVisibility(GONE);
//            amountPending.setText(null);
//        } else {
//            amountPending.setText(pendingAmount);
//            amountPending.setVisibility(VISIBLE);
//        }
    }
}