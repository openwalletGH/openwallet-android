package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.CoinListItem;
import com.coinomi.wallet.util.UiUtils;

/**
 * @author John L. Jegutanis
 */
public abstract class PayWithDialog extends DialogFragment {

    public PayWithDialog() {}

    @Override @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final WalletApplication app = (WalletApplication) getActivity().getApplication();
        final LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View view = inflater.inflate(R.layout.select_pay_with, null);

        final CoinURI uri;
        final CoinType type;
        try {
            uri = new CoinURI(getArguments().getString(Constants.ARG_URI));
            type = uri.getTypeRequired();
        } catch (final CoinURIParseException e) {
            return new DialogBuilder(getActivity())
                    .setMessage(getString(R.string.scan_error, e.getMessage()))
                    .create();
        }

        // Setup accounts that we can send from directly
        ViewGroup typeAccounts = (ViewGroup) view.findViewById(R.id.pay_with_layout);
        boolean canSend = false;
        for (WalletAccount account : app.getAccounts(type)) {
            if (account.getBalance().isPositive()) {
                addPayWithAccountRow(typeAccounts, account, uri);
                canSend = true;
            }
        }

        if (!canSend) {
            UiUtils.setGone(view.findViewById(R.id.pay_with_title));
            UiUtils.setGone(typeAccounts);
        }

        // Setup possible exchange accounts
        ViewGroup exchangeAccounts = (ViewGroup) view.findViewById(R.id.exchange_and_pay_layout);
        boolean canExchange = false;
        for (WalletAccount account : app.getAllAccounts()) {
            if (!account.isType(type) && account.getBalance().isPositive()) {
                addPayWithAccountRow(exchangeAccounts, account, uri);
                canExchange = true;
            }
        }

        if (canExchange) {
            TextView poweredByShapeShift = (TextView) view.findViewById(R.id.powered_by_shapeshift);
            poweredByShapeShift.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.about_shapeshift_title)
                            .setMessage(R.string.about_shapeshift_message)
                            .setPositiveButton(R.string.button_ok, null)
                            .create().show();
                }
            });
        } else {
            UiUtils.setGone(view.findViewById(R.id.exchange_and_pay_title));
            UiUtils.setGone(exchangeAccounts);
            UiUtils.setGone(view.findViewById(R.id.powered_by_shapeshift));
        }

        return new DialogBuilder(getActivity())
                .setView(view)
                .create();
    }

    private void addPayWithAccountRow(final ViewGroup container, final WalletAccount account,
                                      final CoinURI uri) {
        CoinListItem row = new CoinListItem(getActivity());
        row.setAccount(account);
        row.setExchangeRate(null);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                payWith(account, uri);
                dismiss();
            }
        });
        container.addView(row);
    }

    abstract public void payWith(WalletAccount account, CoinURI uri);
}