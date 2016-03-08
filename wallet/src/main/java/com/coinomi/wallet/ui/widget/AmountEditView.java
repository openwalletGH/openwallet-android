package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.text.Editable;
import android.text.Spannable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;
import com.coinomi.core.util.MonetaryFormat;
import com.coinomi.wallet.R;
import com.coinomi.wallet.util.MonetarySpannable;

import org.bitcoinj.core.Coin;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnFocusChange;
import butterknife.OnTextChanged;

/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public class AmountEditView extends RelativeLayout {
    @Bind(R.id.symbol) TextView symbol;
    @Bind(R.id.amount) EditText amount;
    @Bind(R.id.amount_edit_layout) LinearLayout view;
    private Listener listener;
    @Nullable private ValueType type;
    private MonetaryFormat inputFormat;
    private boolean amountSigned = false;
    @Nullable private Value hint;
    private MonetaryFormat hintFormat = new MonetaryFormat().noCode();
    private boolean fireListener = true;

    public interface Listener {
        void changed();
        void focusChanged(final boolean hasFocus);
    }

    public AmountEditView(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.amount_edit, this, true);
        ButterKnife.bind(this);
    }

    public void reset() {
        amount.setText(null);
        symbol.setText(null);
        type = null;
        hint = null;
    }

    public boolean resetType(final ValueType newType) {
        if (type == null || !type.equals(newType)) {
            type = newType;
            hint = null;
            setFormat(newType.getMonetaryFormat());
            return true;
        } else {
            return false;
        }
    }

    public void setFormat(final MonetaryFormat inputFormat) {
        this.inputFormat = inputFormat.noCode();
        hintFormat = inputFormat.noCode();
        updateAppearance();
    }

    public void setType(@Nullable final ValueType type) {
        this.type = type;
        updateAppearance();
    }

    public void setListener(@Nonnull final Listener listener) {
        this.listener = listener;
    }

    public void setHint(@Nullable final Value hint) {
        this.hint = hint;
        updateAppearance();
    }

    public void setAmountSigned(final boolean amountSigned) {
        this.amountSigned = amountSigned;
    }

    public void setSingleLine(boolean isSingleLine) {
        if (isSingleLine) {
            view.setOrientation(LinearLayout.HORIZONTAL);
        } else {
            view.setOrientation(LinearLayout.VERTICAL);
        }
    }

    @CheckForNull
    public Value getAmount() {
        final String str = amount.getText().toString().trim();
        Value amount = null;

        try {
            if (!str.isEmpty()) {
                if (type != null) {
                    amount = inputFormat.parse(type, str);
                }
            }
        } catch (final Exception x) { /* ignored */ }

        return amount;
    }

    public void setAmount(@Nullable final Value value, final boolean fireListener) {
        if (!fireListener) setFireListener(false);

        if (value != null) {
            amount.setText(new MonetarySpannable(inputFormat, amountSigned, value));
        } else {
            amount.setText(null);
        }

        if (!fireListener) setFireListener(true);
    }

    public String getAmountText() {
        return amount.getText().toString().trim();
    }

    public TextView getAmountView() {
        return amount;
    }

    private void updateAppearance() {
        if (type != null) {
            symbol.setText(type.getSymbol());
            symbol.setVisibility(VISIBLE);
        } else {
            symbol.setText(null);
            symbol.setVisibility(GONE);
        }

        final Spannable hintSpannable = new MonetarySpannable(hintFormat, amountSigned,
                hint != null ? hint : Coin.ZERO);
        amount.setHint(hintSpannable);
    }

    private void setFireListener(boolean fireListener) {
        this.fireListener = fireListener;
    }

    private boolean isFireListener() {
        return fireListener;
    }

    @OnTextChanged(value = R.id.amount, callback = OnTextChanged.Callback.AFTER_TEXT_CHANGED)
    public void afterTextChanged(final Editable s) {
        // workaround for German keyboards
        final String original = s.toString();
        final String replaced = original.replace(',', '.');
        if (!replaced.equals(original)) {
            s.clear();
            s.append(replaced);
        }
    }

    @OnTextChanged(value = R.id.amount, callback = OnTextChanged.Callback.TEXT_CHANGED)
    public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
        if (listener != null && isFireListener()) listener.changed();
    }

    @OnFocusChange(R.id.amount)
    public void onFocusChange(final View v, final boolean hasFocus) {
        if (!hasFocus) {
            final Value amount = getAmount();
            if (amount != null)
                setAmount(amount, false);
        }

        if (listener != null && isFireListener()) listener.focusChanged(hasFocus);
    }
}
