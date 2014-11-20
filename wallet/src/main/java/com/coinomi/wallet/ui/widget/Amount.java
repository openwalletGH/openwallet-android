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
    private final TextView connectionStatus;
    private final TextView disconnetSymbol;
    private final Context context;
//    private final TextView amountPending;
    boolean isBig = false;

    public Amount(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.context = context;

        LayoutInflater.from(context).inflate(R.layout.amount, this, true);

        symbol = (TextView) findViewById(R.id.symbol);
//        amountPending = (TextView) findViewById(R.id.amount_pending);
//        amountPending.setVisibility(GONE);
        connectionStatus = (TextView) findViewById(R.id.connection_status);
        Fonts.setTypeface(connectionStatus, Fonts.Font.COINOMI_FONT_ICONS);
        disconnetSymbol = (TextView) findViewById(R.id.disconnect_symbol);
        Fonts.setTypeface(disconnetSymbol, Fonts.Font.COINOMI_FONT_ICONS);

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

    public void setConnectivity(WalletPocketConnectivity connectivity) {
        switch (connectivity) {
            case WORKING:
                // TODO support WORKING state
            case CONNECTED:
                disconnetSymbol.setVisibility(INVISIBLE);
                connectionStatus.setTextColor(getResources().getColor(R.color.gray_54_sec_text_icons));
                break;
            default:
            case DISCONNECTED:
                disconnetSymbol.setVisibility(VISIBLE);
                connectionStatus.setTextColor(getResources().getColor(R.color.gray_26_hint_text));
        }
    }
}