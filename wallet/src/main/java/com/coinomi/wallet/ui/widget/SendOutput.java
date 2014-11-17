package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.coinomi.core.util.GenericUtils;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.Fonts;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

/**
 * @author Giannis Dzegoutanis
 */
public class SendOutput extends LinearLayout {
    private final TextView sendType;
    private final TextView amount;
    private final TextView symbol;
    private final TextView address;

    private boolean isAddressExpanded = false;

    public SendOutput(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.transaction_output, this, true);

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

        getRootView().setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isAddressExpanded) {
                    address.setSingleLine(true);
                    address.setMaxLines(1);
                    isAddressExpanded = false;
                } else {
                    address.setSingleLine(false);
                    address.setMaxLines(5);
                    isAddressExpanded = true;
                }
            }
        });
    }

    public void setAmount(String amount) {
        this.amount.setText(amount);
    }

    public void setSymbol(String symbol) {
        this.symbol.setText(symbol);
    }

    public void setAddress(String address) {
        this.address.setText(GenericUtils.addressSplitToGroups(address));
    }

    public void setFee(boolean isFee) {
        if (isFee) {
            sendType.setText(R.string.fee);
            address.setVisibility(GONE);
        } else {
            if (!sendType.isInEditMode()) { // If not displayed within a developer tool
                Fonts.setTypeface(sendType, Fonts.Font.ENTYPO_COINOMI);
                sendType.setText(getResources().getString(R.string.font_icon_send));
            }
        }
    }
}
