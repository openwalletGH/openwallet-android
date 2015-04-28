package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.WalletUtils;

import org.bitcoinj.core.Address;

/**
 * @author John L. Jegutanis
 */
public class AddressView extends LinearLayout {
    private ImageView iconView;
    private TextView addressLabelView;
    private TextView addressView;

    private boolean isMultiLine;
    private boolean isIconShown;
    private Address address;

    public AddressView(Context context) {
        super(context);

        inflateLayout(context);
    }

    public AddressView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.Address, 0, 0);
        try {
            isMultiLine = a.getBoolean(R.styleable.Address_multi_line, false);
            isIconShown = a.getBoolean(R.styleable.Address_show_coin_icon, false);
        } finally {
            a.recycle();
        }

        inflateLayout(context);
    }

    private void inflateLayout(Context context) {
        LayoutInflater.from(context).inflate(R.layout.address, this, true);
        iconView = (ImageView) findViewById(R.id.icon);
        addressLabelView = (TextView) findViewById(R.id.address_label);
        addressView = (TextView) findViewById(R.id.address);
    }

    public void setAddressAndLabel(Address address) {
        this.address = address;
        updateView();
    }

    public void setMultiLine(boolean isMultiLine) {
        this.isMultiLine = isMultiLine;
        updateView();
    }

    public void setIconShown(boolean isIconShown) {
        this.isIconShown = isIconShown;
        updateView();
    }

    private void updateView() {
        String label = AddressBookProvider.resolveLabel(getContext(), address);
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
        if (isIconShown) {
            iconView.setVisibility(VISIBLE);
            iconView.setContentDescription(((CoinType) address.getParameters()).getName());
            iconView.setImageResource(WalletUtils.getIconRes((CoinType) address.getParameters()));
        } else {
            iconView.setVisibility(GONE);
        }
    }
}