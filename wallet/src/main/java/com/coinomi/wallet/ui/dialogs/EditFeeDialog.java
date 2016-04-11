package com.coinomi.wallet.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.TextView;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.DialogBuilder;
import com.coinomi.wallet.ui.widget.AmountEditView;

import butterknife.Bind;
import butterknife.ButterKnife;

import static com.coinomi.core.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class EditFeeDialog extends DialogFragment {
    @Bind(R.id.fee_description)
    TextView description;
    @Bind(R.id.fee_amount)
    AmountEditView feeAmount;
    Configuration configuration;
    Resources resources;

    public static EditFeeDialog newInstance(ValueType type) {
        EditFeeDialog dialog = new EditFeeDialog();
        dialog.setArguments(new Bundle());
        dialog.getArguments().putString(Constants.ARG_COIN_ID, type.getId());
        return dialog;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        WalletApplication application = (WalletApplication) activity.getApplication();
        configuration = application.getConfiguration();
        resources = application.getResources();
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        checkState(getArguments().containsKey(Constants.ARG_COIN_ID), "Must provide coin id");
        View view = View.inflate(getActivity(), R.layout.edit_fee_dialog, null);
        ButterKnife.bind(this, view);

        // TODO move to xml
        feeAmount.setSingleLine(true);

        final CoinType type = CoinID.typeFromId(getArguments().getString(Constants.ARG_COIN_ID));
        feeAmount.resetType(type);

        String feePolicy;
        switch (type.getFeePolicy()) {
            case FEE_PER_KB:
                feePolicy = resources.getString(R.string.tx_fees_per_kilobyte);
                break;
            case FLAT_FEE:
                feePolicy = resources.getString(R.string.tx_fees_per_transaction);
                break;
            default:
                throw new RuntimeException("Unknown fee policy " + type.getFeePolicy());
        }
        description.setText(resources.getString(R.string.tx_fees_description, feePolicy));

        final Value fee = configuration.getFeeValue(type);
        feeAmount.setAmount(fee, false);

        final DialogBuilder builder = new DialogBuilder(getActivity());
        builder.setTitle(resources.getString(R.string.tx_fees_title, type.getName()));
        builder.setView(view);
        DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        Value newFee = feeAmount.getAmount();
                        if (newFee != null && !newFee.equals(fee)) {
                            configuration.setFeeValue(newFee);
                        }
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        configuration.resetFeeValue(type);
                        break;
                }


            }
        };
        builder.setNegativeButton(R.string.button_cancel, onClickListener);
        builder.setNeutralButton(R.string.button_default, onClickListener);
        builder.setPositiveButton(R.string.button_ok, onClickListener);

        return builder.create();
    }
}
