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
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.R;

/**
 * @author John L. Jegutanis
 */
public class SendOutput extends LinearLayout {
    private final Context context;
    private TextView sendTypeText;
    private TextView amount;
    private TextView symbol;
    private TextView amountLocal;
    private TextView symbolLocal;
    private TextView addressLabelView;
    private TextView addressView;

    private AbstractAddress address;
    private String label;
    private boolean isSending;
    private String sendLabel;
    private String receiveLabel;
    private String feeLabel;

    public SendOutput(Context context) {
        super(context);
        this.context = context;

        inflateView(context);
    }

    public SendOutput(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

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

        sendTypeText = (TextView) findViewById(R.id.send_output_type_text);
        amount = (TextView) findViewById(R.id.amount);
        symbol = (TextView) findViewById(R.id.symbol);
        amountLocal = (TextView) findViewById(R.id.local_amount);
        symbolLocal = (TextView) findViewById(R.id.local_symbol);
        addressLabelView = (TextView) findViewById(R.id.output_label);
        addressView = (TextView) findViewById(R.id.output_address);

        amountLocal.setVisibility(GONE);
        symbolLocal.setVisibility(GONE);
        addressLabelView.setVisibility(View.GONE);
        addressView.setVisibility(View.GONE);
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

    public void setAddress(AbstractAddress address) {
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
        } else if (address != null) {
            addressLabelView.setText(GenericUtils.addressSplitToGroups(address));
            addressLabelView.setTypeface(Typeface.MONOSPACE);
            addressLabelView.setVisibility(View.VISIBLE);
            addressView.setVisibility(View.GONE);
        } else {
            addressLabelView.setVisibility(View.GONE);
            addressView.setVisibility(View.GONE);
        }
    }

    public void setLabel(String label) {
        this.label = label;
        updateView();
    }

    public void setIsFee(boolean isFee) {
        if (isFee) {
            setTypeLabel(getFeeLabel());
            addressLabelView.setVisibility(GONE);
            addressView.setVisibility(GONE);
        } else {
            updateDirectionLabels();
        }
    }

    private void updateDirectionLabels() {
        if (isSending) {
            setTypeLabel(getSendLabel());
        } else {
            setTypeLabel(getReceiveLabel());
        }
    }

    private void setTypeLabel(String typeLabel) {
        if (typeLabel.isEmpty()) {
            sendTypeText.setVisibility(GONE);
        } else {
            sendTypeText.setVisibility(VISIBLE);
            sendTypeText.setText(typeLabel);
        }
    }

    private String getSendLabel() {
        if (sendLabel == null) {
            return getResources().getString(R.string.send);
        } else {
            return sendLabel;
        }
    }

    private String getReceiveLabel() {
        if (receiveLabel == null) {
            return getResources().getString(R.string.receive);
        } else {
            return receiveLabel;
        }
    }

    private String getFeeLabel() {
        if (feeLabel == null) {
            return getResources().getString(R.string.fee);
        } else {
            return feeLabel;
        }
    }

    public void setSendLabel(String sendLabel) {
        this.sendLabel = sendLabel;
        updateDirectionLabels();
    }

    public void setReceiveLabel(String receiveLabel) {
        this.receiveLabel = receiveLabel;
        updateDirectionLabels();
    }

    public void setFeeLabel(String feeLabel) {
        this.feeLabel = feeLabel;
        updateDirectionLabels();
    }

    public void setSending(boolean isSending) {
        this.isSending = isSending;
        updateDirectionLabels();
    }

    public void setLabelAndAddress(AbstractAddress address) {
        this.label = AddressBookProvider.resolveLabel(context, address);
        this.address = address;
        updateView();
    }

    public void hideLabelAndAddress() {
        this.label = null;
        this.address = null;
        updateView();
    }
}
