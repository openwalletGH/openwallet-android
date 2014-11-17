package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ProgressBar;
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
    private final TextView amount;
    private final TextView symbol;
    private final TextView connectionStatus;
//    private final TextView amountPending;

    boolean isBig = false;

    public Amount(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.amount, this, true);

        amount = (TextView) findViewById(R.id.amount);
        symbol = (TextView) findViewById(R.id.symbol);
//        amountPending = (TextView) findViewById(R.id.amount_pending);
//        amountPending.setVisibility(GONE);
        connectionStatus = (TextView) findViewById(R.id.connection_status);
//        Fonts.setTypeface(connectionStatus, Fonts.Font.ENTYPO_COINOMI);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Amount, 0, 0);
        try {
            isBig = a.getBoolean(R.styleable.Amount_show_big, false);
        } finally {
            a.recycle();
        }

        if (isBig) {
            amount.setTextAppearance(context, R.style.AmountBig);
        }

        if (getRootView().isInEditMode()) {
            setAmount("3.14159265");
        }
    }

    public void setAmount(String amount) {
        this.amount.setText(amount);
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

    public void setConnectivity(WalletPocketConnectivity connectivity) {
        switch (connectivity) {
            case WORKING:
                // TODO support WORKING state
            case CONNECTED:
                connectionStatus.setVisibility(VISIBLE);
                connectionStatus.setText("C");
                break;
            default:
            case DISCONNECTED:
                connectionStatus.setVisibility(VISIBLE);
                connectionStatus.setText("D");
        }
    }
}