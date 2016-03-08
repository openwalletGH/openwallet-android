package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.coinomi.wallet.R;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public class Amount extends LinearLayout {
    private final FontFitTextView amountView;
    private final TextView symbolView;
//    private final TextView amountPending;
    boolean isBig = false;
    boolean isSmall = false;
    boolean isSingleLine = false;

    public Amount(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.amount, this, true);

        symbolView = (TextView) findViewById(R.id.symbol);
//        amountPending = (TextView) findViewById(R.id.amount_pending);
//        amountPending.setVisibility(GONE);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Amount, 0, 0);
        try {
            isBig = a.getBoolean(R.styleable.Amount_show_big, false);
            isSmall = a.getBoolean(R.styleable.Amount_show_small, false);
            isSingleLine = a.getBoolean(R.styleable.Amount_single_line, false);
        } finally {
            a.recycle();
        }

        amountView = (FontFitTextView) findViewById(R.id.amount_text);

        if (!getRootView().isInEditMode()) {
            if (isBig) {
                amountView.setTextAppearance(context, R.style.AmountBig);
                symbolView.setTextAppearance(context, R.style.AmountSymbolBig);
            } else if (isSmall) {
                amountView.setTextAppearance(context, R.style.AmountSmall);
                symbolView.setTextAppearance(context, R.style.AmountSymbolSmall);
            } else {
                amountView.setTextAppearance(context, R.style.Amount);
                symbolView.setTextAppearance(context, R.style.AmountSymbol);
            }
        }

        amountView.setMaxTextSize(amountView.getTextSize());

        setSingleLine(isSingleLine);

        if (getRootView().isInEditMode()) {
            amountView.setText("3.14159265");
        }
    }

    public void setAmount(CharSequence amount) {
        amountView.setText(amount);
    }

    public void setSymbol(CharSequence symbol) {
        symbolView.setText(symbol);
    }

    public void setSingleLine(boolean isSingleLine) {
        this.isSingleLine = isSingleLine;
        if (isSingleLine) {
            ((LinearLayout)findViewById(R.id.amount_layout)).setOrientation(HORIZONTAL);
        } else {
            ((LinearLayout)findViewById(R.id.amount_layout)).setOrientation(VERTICAL);
        }
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