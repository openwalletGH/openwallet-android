package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.coinomi.core.util.GenericUtils;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;

/**
 * @author John L. Jegutanis
 */
public class SendOutput extends LinearLayout {
    private TextView sendType;
    private TextView amount;
    private TextView symbol;
    private TextView amountLocal;
    private TextView symbolLocal;
    private TextView addressLabelView;
    private TextView addressView;

    private String address;
    private String label;
    private boolean isSending;

    public SendOutput(Context context) {
        super(context);

        inflateView(context);
    }

    public SendOutput(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflateView(context);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SendOutput, 0, 0);
        try {
            setIsFee(a.getBoolean(R.styleable.SendOutput_is_fee, false));
        } finally {
            a.recycle();
        }
    }

    private void inflateView(Context context) {
        LayoutInflater.from(context).inflate(R.layout.transaction_output, this, true);

        sendType = (TextView) findViewById(R.id.send_output_type);
        amount = (TextView) findViewById(R.id.amount);
        symbol = (TextView) findViewById(R.id.symbol);
        amountLocal = (TextView) findViewById(R.id.local_amount);
        symbolLocal = (TextView) findViewById(R.id.local_symbol);
        addressLabelView = (TextView) findViewById(R.id.output_label);
        addressView = (TextView) findViewById(R.id.output_address);

        amountLocal.setVisibility(GONE);
        symbolLocal.setVisibility(GONE);
    }

    public void setAmount(String amount) {
        this.amount.setText(amount);
    }

    public void setSymbol(String symbol) {
        this.symbol.setText(symbol);
    }

    public void setAmountLocal(String amount) {
        this.amountLocal.setText(amount);
        this.amountLocal.setVisibility(VISIBLE);
    }

    public void setSymbolLocal(String symbol) {
        this.symbolLocal.setText(symbol);
        this.symbolLocal.setVisibility(VISIBLE);
    }

    public void setAddress(String address) {
        this.address = address;
        updateView();
    }

    private void updateView() {
        if (label != null) {
            addressLabelView.setText(label);
            addressLabelView.setTypeface(Typeface.DEFAULT);
            addressLabelView.setVisibility(View.VISIBLE);
            if (address != null) {
                addressView.setText(GenericUtils.addressSplitToGroups(address));
                addressView.setVisibility(View.VISIBLE);
            } else {
                addressView.setVisibility(View.GONE);
            }
        } else {
            addressLabelView.setText(GenericUtils.addressSplitToGroups(address));
            addressLabelView.setTypeface(Typeface.MONOSPACE);
            addressLabelView.setVisibility(View.VISIBLE);
            addressView.setVisibility(View.GONE);
        }
    }

    public void setLabel(String label) {
        this.label = label;
        updateView();
    }

    public void setIsFee(boolean isFee) {
        if (isFee) {
            sendType.setText(R.string.fee);
            addressLabelView.setVisibility(GONE);
            addressView.setVisibility(GONE);
        } else {
            if (!sendType.isInEditMode()) { // If not displayed within a developer tool
                updateIcon();
            }
        }
    }

    private void updateIcon() {
        Fonts.setTypeface(sendType, Fonts.Font.COINOMI_FONT_ICONS);
        if (isSending) {
            sendType.setText(getResources().getString(R.string.font_icon_send_coins));
        } else {
            sendType.setText(getResources().getString(R.string.font_icon_receive_coins));
        }
    }

    public void setSending(boolean isSending) {
        this.isSending = isSending;
        updateIcon();
    }

    public void setLabelAndAddress(String label, String address) {
        this.label = label;
        this.address = address;
        updateView();
    }
}
