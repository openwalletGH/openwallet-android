package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.TransactionOutput;

/**
 * @author Giannis Dzegoutanis
 */
public class SendOutput extends LinearLayout {
    private final TextView sendType;
    private final TextView amount;
    private final TextView symbol;
    private final TextView address;

    private boolean isFee;

    public SendOutput(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.sent_output, this, true);

        sendType = (TextView) findViewById(R.id.send_output_type);
        amount = (TextView) findViewById(R.id.amount);
        symbol = (TextView) findViewById(R.id.symbol);
        address = (TextView) findViewById(R.id.send_to_address);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SendOutput, 0, 0);
        try {
            setFee(a.getBoolean(R.styleable.SendOutput_is_fee, false));
        } finally {
            a.recycle();
        }

        Fonts.setTypeface(amount, Fonts.Font.ROBOTO_REGULAR);
        Fonts.setTypeface(symbol, Fonts.Font.ROBOTO_LIGHT);
        Fonts.setTypeface(address, Fonts.Font.UBUNTU_MONO_REGULAR);
    }

    public void setAmount(Coin amount) {
        this.amount.setText(amount.toPlainString());
    }

    public void setSymbol(String symbol) {
        this.symbol.setText(symbol);
    }

    public void setAddress(Address address) {
        this.address.setText(address.toString());
    }

    public void setAddress(String address) {
        this.address.setText(address);
    }

    public void setFee(boolean isFee) {
        this.isFee = isFee;

        if (this.isFee) {
            Fonts.setTypeface(sendType, Fonts.Font.ROBOTO_REGULAR);
            sendType.setText(R.string.fee);
            address.setVisibility(GONE);
        } else {
            if (!sendType.isInEditMode()) { // If not displayed within a developer tool
                Fonts.setTypeface(sendType, Fonts.Font.FONT_AWESOME);
                sendType.setText(String.valueOf(Constants.CHAR_FONT_AWESOME_SEND));
            }
        }
    }
}
