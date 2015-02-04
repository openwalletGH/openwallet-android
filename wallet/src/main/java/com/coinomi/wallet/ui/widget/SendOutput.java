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
    private static final int MAX_LINES = 5;
    private final TextView sendType;
    private final TextView amount;
    private final TextView symbol;
    private final TextView addressOrLabel;
    private final TextView amountLocal;
    private final TextView symbolLocal;

    private boolean isAddressLabelExpanded = false;
    private String address;
    private String label;
    private boolean isSending;

    public SendOutput(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.transaction_output, this, true);

        sendType = (TextView) findViewById(R.id.send_output_type);
        amount = (TextView) findViewById(R.id.amount);
        symbol = (TextView) findViewById(R.id.symbol);
        amountLocal = (TextView) findViewById(R.id.local_amount);
        symbolLocal = (TextView) findViewById(R.id.local_symbol);
        addressOrLabel = (TextView) findViewById(R.id.send_to_address);

        amountLocal.setVisibility(GONE);
        symbolLocal.setVisibility(GONE);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SendOutput, 0, 0);
        try {
            setIsFee(a.getBoolean(R.styleable.SendOutput_is_fee, false));
        } finally {
            a.recycle();
        }

        getRootView().setOnClickListener(getOnClickListener());
    }

    private OnClickListener getOnClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAddressLabelExpanded) {
                    addressOrLabel.setSingleLine(true);
                    addressOrLabel.setMaxLines(1);
                    isAddressLabelExpanded = false;
                } else {
                    addressOrLabel.setSingleLine(false);
                    addressOrLabel.setMaxLines(MAX_LINES);
                    isAddressLabelExpanded = true;
                }
                updateAddressLabel();
            }
        };
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
        label = null;
        this.address = address;
        updateAddressLabel();
    }

    private void updateAddressLabel() {
        if (address != null) {
            addressOrLabel.setTypeface(Typeface.MONOSPACE);
            addressOrLabel.setText(GenericUtils.addressSplitToGroups(address));
        } else if (label != null) {
            addressOrLabel.setTypeface(Typeface.DEFAULT);
            addressOrLabel.setText(label);
        }
    }

    public void setLabel(String label) {
        address = null;
        this.label = label;
        updateAddressLabel();
    }

    public void setIsFee(boolean isFee) {
        if (isFee) {
            sendType.setText(R.string.fee);
            addressOrLabel.setVisibility(GONE);
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
}
