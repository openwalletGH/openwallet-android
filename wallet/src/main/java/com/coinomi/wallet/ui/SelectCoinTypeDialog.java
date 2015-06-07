package com.coinomi.wallet.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.ui.widget.AddressView;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John L. Jegutanis
 */
abstract public class SelectCoinTypeDialog extends DialogFragment {
    private static final Logger log = LoggerFactory.getLogger(SelectCoinTypeDialog.class);

    public SelectCoinTypeDialog() {}

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        DialogBuilder builder = new DialogBuilder(getActivity());
        String addressStr = args.getString(Constants.ARG_ADDRESS_STRING);
        List<CoinType> possibleTypes;
        try {
            possibleTypes = GenericUtils.getPossibleTypes(addressStr);
        } catch (AddressFormatException e) {
            log.error("Supplied invalid address: " + addressStr);
            possibleTypes = new ArrayList<>(0);
        }

        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View view = inflater.inflate(R.layout.select_coin_for_address, null);
        ViewGroup container = (ViewGroup) view.findViewById(R.id.pay_as_layout);
        int paddingBottom = getResources().getDimensionPixelSize(R.dimen.activity_vertical_margin);

        AddressView addressView = null;
        for (CoinType type : possibleTypes) {
            try {
                final Address address = new Address(type, addressStr);
                addressView = new AddressView(getActivity());
                addressView.setPadding(0, 0, 0, paddingBottom);
                addressView.setAddressAndLabel(address);
                addressView.setIconShown(true);
                addressView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onAddressSelected(address);
                        SelectCoinTypeDialog.this.dismiss();
                    }
                });
                container.addView(addressView);
            } catch (AddressFormatException e) { /* should not happen*/ }
        }
        if (addressView != null) addressView.setPadding(0, 0, 0, 0); // remove padding from last one

        return builder.setTitle(R.string.ambiguous_address_title).setView(view).create();
    }

    abstract public void onAddressSelected(Address address);
}