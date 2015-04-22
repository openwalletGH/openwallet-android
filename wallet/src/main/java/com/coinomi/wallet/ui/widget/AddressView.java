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
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.R;

import org.bitcoinj.core.Address;

/**
 * @author John L. Jegutanis
 */
public class AddressView extends LinearLayout {
    private final TextView addressLabelView;
    private final TextView addressView;
    private final Context context;
    private final boolean isMultiLine;

    public AddressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Amount, 0, 0);
        try {
            isMultiLine = a.getBoolean(R.styleable.AddressView_multi_line, false);
        } finally {
            a.recycle();
        }

        LayoutInflater.from(context).inflate(R.layout.address, this, true);

        addressLabelView = (TextView) findViewById(R.id.address_label);
        addressView = (TextView) findViewById(R.id.address);
    }

    public void setAddressAndLabel(Address address) {
        String label = AddressBookProvider.resolveLabel(context, address);
        if (label != null) {
            addressLabelView.setText(label);
            addressLabelView.setTypeface(Typeface.DEFAULT);
            addressView.setText(
                    GenericUtils.addressSplitToGroups(address.toString()));
            addressView.setVisibility(View.VISIBLE);
        } else {
            if (isMultiLine) {
                addressLabelView.setText(
                        GenericUtils.addressSplitToGroupsMultiline(address.toString()));
            } else {
                addressLabelView.setText(
                        GenericUtils.addressSplitToGroups(address.toString()));
            }
            addressLabelView.setTypeface(Typeface.MONOSPACE);
            addressView.setVisibility(View.GONE);
        }
    }
}