package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
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

/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public class AmountEditView extends RelativeLayout {
    private final TextView symbol;
    private final EditText textView;
    private Listener listener;
    @Nullable private ValueType type;
    private MonetaryFormat inputFormat;
    private boolean amountSigned = false;
    @Nullable private Value hint;
    private MonetaryFormat hintFormat = new MonetaryFormat().noCode();

    public static interface Listener {
        void changed();
        void focusChanged(final boolean hasFocus);
    }

    public AmountEditView(Context context, AttributeSet attrs) {
        super(context, attrs);

        LayoutInflater.from(context).inflate(R.layout.amount_edit, this, true);

        textView = (EditText) findViewById(R.id.amount);
        symbol = (TextView) findViewById(R.id.symbol);

        textView.addTextChangedListener(textViewListener);
        textView.setOnFocusChangeListener(textViewListener);
    }

    public void reset() {
        textView.setText(null);
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

    @CheckForNull
    public Value getAmount() {
        final String str = textView.getText().toString().trim();
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
        if (!fireListener) textViewListener.setFire(false);

        if (value != null) {
            textView.setText(new MonetarySpannable(inputFormat, amountSigned, value));
        } else {
            textView.setText(null);
        }

        if (!fireListener) textViewListener.setFire(true);
    }

    public String getAmountText() {
        return textView.getText().toString().trim();
    }

    public TextView getTextView() {
        return textView;
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
        textView.setHint(hintSpannable);
    }

    private final TextViewListener textViewListener = new TextViewListener();

    private final class TextViewListener implements TextWatcher, OnFocusChangeListener {
        private boolean fire = true;

        public void setFire(final boolean fire) {
            this.fire = fire;
        }

        @Override
        public void afterTextChanged(final Editable s) {
            // workaround for German keyboards
            final String original = s.toString();
            final String replaced = original.replace(',', '.');
            if (!replaced.equals(original)) {
                s.clear();
                s.append(replaced);
            }
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            if (listener != null && fire) listener.changed();
        }

        @Override
        public void onFocusChange(final View v, final boolean hasFocus) {
            if (!hasFocus) {
                final Value amount = getAmount();
                if (amount != null)
                    setAmount(amount, false);
            }

            if (listener != null && fire) listener.focusChanged(hasFocus);
        }
    }
}
