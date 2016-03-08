package com.coinomi.wallet.ui;

/*
 * Copyright 2013-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.view.View;

import com.coinomi.core.coins.Value;
import com.coinomi.core.util.ExchangeRate;
import com.coinomi.wallet.ui.widget.AmountEditView;
import com.coinomi.wallet.ui.widget.AmountEditView.Listener;

import org.bitcoinj.core.Coin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public final class CurrencyCalculatorLink {
    private final AmountEditView coinAmountView;
    private final AmountEditView localAmountView;

    private Listener listener = null;
    private boolean enabled = true;
    private ExchangeRate exchangeRate = null;
    @Nullable private boolean exchangeDirection = true;

    private final AmountEditView.Listener coinAmountViewListener = new AmountEditView.Listener() {
        @Override
        public void changed() {
            if (coinAmountView.getAmount() != null)
                setExchangeDirection(true);
            else
                localAmountView.setHint(null);

            if (listener != null)
                listener.changed();
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (listener != null)
                listener.focusChanged(hasFocus);
        }
    };

    private final AmountEditView.Listener localAmountViewListener = new AmountEditView.Listener() {
        @Override
        public void changed() {
            if (localAmountView.getAmount() != null)
                setExchangeDirection(false);
            else
                coinAmountView.setHint(null);

            if (listener != null)
                listener.changed();
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (listener != null)
                listener.focusChanged(hasFocus);
        }
    };

    public CurrencyCalculatorLink(@Nonnull final AmountEditView coinAmountView,
                                  @Nonnull final AmountEditView localAmountView) {
        this.coinAmountView = coinAmountView;
        this.coinAmountView.setListener(coinAmountViewListener);

        this.localAmountView = localAmountView;
        this.localAmountView.setListener(localAmountViewListener);

        update();
    }

    public void setListener(@Nullable final Listener listener) {
        this.listener = listener;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;

        update();
    }

    public void setExchangeRate(@Nullable final ExchangeRate exchangeRate) {
        this.exchangeRate = exchangeRate;

        update();
    }

    @Nullable
    public Coin getPrimaryAmountCoin() {
        Value value = getPrimaryAmount();
        return value != null ? value.toCoin() : null;
    }

    @Nullable
    public Value getPrimaryAmount() {
        if (exchangeDirection) {
            return coinAmountView.getAmount();
        } else if (exchangeRate != null) {
            final Value localAmount = localAmountView.getAmount();
            try {
                return localAmount != null ? convertSafe(localAmount) : null;
            } catch (ArithmeticException x) {
                return null;
            }
        } else {
            return null;
        }
    }

    @Nullable
    public Value getSecondaryAmount() {
        if (exchangeRate == null) return null;

        if (exchangeDirection) {
            final Value coinAmount = coinAmountView.getAmount();
            try {
                return coinAmount != null ? convertSafe(coinAmount) : null;
            } catch (ArithmeticException x) {
                return null;
            }
        } else {
            return localAmountView.getAmount();
        }
    }

    /**
     * Get the amount that the user entered, could be either type
     */
    public Value getRequestedAmount() {
        if (exchangeDirection) {
            return coinAmountView.getAmount();
        } else {
            return localAmountView.getAmount();
        }
    }

    public boolean hasAmount() {
        return getPrimaryAmount() != null;
    }

    private void update() {
        coinAmountView.setEnabled(enabled);

        if (exchangeRate != null) {
            localAmountView.setEnabled(enabled);
            localAmountView.resetType(exchangeRate.getDestinationType());
            localAmountView.setVisibility(View.VISIBLE);

            coinAmountView.resetType(exchangeRate.getSourceType());

            if (exchangeDirection) {
                final Value coinAmount = coinAmountView.getAmount();
                if (coinAmount != null) {
                    localAmountView.setAmount(null, false);
                    localAmountView.setHint(convertSafe(coinAmount));
                    coinAmountView.setHint(null);
                }
            } else {
                final Value localAmount = localAmountView.getAmount();
                if (localAmount != null) {
                    localAmountView.setHint(null);
                    coinAmountView.setAmount(null, false);
                    coinAmountView.setHint(convertSafe(localAmount));
                }
            }
        } else {
            localAmountView.setEnabled(false);
            localAmountView.setHint(null);
            localAmountView.setVisibility(View.INVISIBLE);
            coinAmountView.setHint(null);
        }
    }

    /**
     * Makes an exchange and in case of an error returns a null.
     */
    @Nullable
    private Value convertSafe(Value amount) {
        try {
            return exchangeRate.convert(amount);
        } catch (Exception e) {
            return null;
        }
    }

    public void setExchangeDirection(final boolean exchangeDirection) {
        this.exchangeDirection = exchangeDirection;

        update();
    }

    public boolean getExchangeDirection() {
        return exchangeDirection;
    }

    public View activeTextView() {
        if (exchangeDirection)
            return coinAmountView.getAmountView();
        else
            return localAmountView.getAmountView();
    }

    public void requestFocus() {
        activeTextView().requestFocus();
    }

    public void setExchangeRateHints(@Nullable final Value primaryAmount) {
        if (exchangeRate != null) {
            coinAmountView.setHint(primaryAmount);
            localAmountView.setHint(convertSafe(primaryAmount));
        }
    }

    public void setPrimaryAmount(@Nullable final Value amount) {
        final Listener listener = this.listener;
        this.listener = null;

        coinAmountView.setAmount(amount, true);

        this.listener = listener;
    }

    public boolean isEmpty() {
        return coinAmountView.getAmountText().isEmpty() && localAmountView.getAmountText().isEmpty();
    }

//    public void setNextFocusId(final int nextFocusId)
//    {
//        coinAmountView.setNextFocusId(nextFocusId);
//        localAmountView.setNextFocusId(nextFocusId);
//    }
}

