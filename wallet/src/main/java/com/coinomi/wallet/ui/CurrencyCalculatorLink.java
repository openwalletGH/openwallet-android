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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import android.view.View;

import com.coinomi.wallet.ui.widget.AmountEditView;
import com.coinomi.wallet.ui.widget.AmountEditView.Listener;


/**
 * @author Andreas Schildbach
 */
public final class CurrencyCalculatorLink {
    private final AmountEditView coinAmountView;
    private final AmountEditView localAmountView;

    private Listener listener = null;
    private boolean enabled = true;
    private ExchangeRate exchangeRate = null;
    private boolean exchangeDirection = true;

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

    public CurrencyCalculatorLink(@Nonnull final AmountEditView coinAmountView, @Nonnull final AmountEditView localAmountView) {
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

    public void setExchangeRate(@Nonnull final ExchangeRate exchangeRate) {
        this.exchangeRate = exchangeRate;

        update();
    }

    @CheckForNull
    public Coin getAmount() {
        if (exchangeDirection) {
            return (Coin) coinAmountView.getAmount();
        } else if (exchangeRate != null) {
            final Fiat localAmount = (Fiat) localAmountView.getAmount();
            try {
                return localAmount != null ? exchangeRate.fiatToCoin(localAmount) : null;
            } catch (ArithmeticException x) {
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean hasAmount() {
        return getAmount() != null;
    }

    private void update() {
        coinAmountView.setEnabled(enabled);

        if (exchangeRate != null) {
            localAmountView.setEnabled(enabled);
            localAmountView.setLocalCurrency(exchangeRate.fiat.currencyCode);
            localAmountView.setVisibility(View.VISIBLE);

            if (exchangeDirection) {
                final Coin coinAmount = (Coin) coinAmountView.getAmount();
                if (coinAmount != null) {
                    localAmountView.setAmount(null, false);
                    localAmountView.setHint(exchangeRate.coinToFiat(coinAmount));
                    coinAmountView.setHint(null);
                }
            } else {
                final Fiat localAmount = (Fiat) localAmountView.getAmount();
                if (localAmount != null) {
                    localAmountView.setHint(null);
                    coinAmountView.setAmount(null, false);
                    try {
                        coinAmountView.setHint(exchangeRate.fiatToCoin(localAmount));
                    } catch (final ArithmeticException x) {
                        coinAmountView.setHint(null);
                    }
                }
            }
        } else {
            localAmountView.setEnabled(false);
            localAmountView.setHint(null);
            localAmountView.setVisibility(View.INVISIBLE);
            coinAmountView.setHint(null);
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
            return coinAmountView.getTextView();
        else
            return localAmountView.getTextView();
    }

    public void requestFocus() {
        activeTextView().requestFocus();
    }

    public void setCoinAmount(@Nullable final Coin amount) {
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

