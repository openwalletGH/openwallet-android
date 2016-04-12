package com.coinomi.wallet.util;

/*
 * Copyright 2011-2014 the original author or authors.
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


import android.graphics.Typeface;
import android.os.Build;
import android.text.Spannable;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.families.NxtFamily;
import com.coinomi.core.util.Currencies;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.AbstractTransaction.AbstractOutput;
import com.coinomi.core.wallet.AbstractWallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;

import org.bitcoinj.core.Sha256Hash;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkState;

/**
 * @author Andreas Schildbach
 * @author John L. Jegutanis
 */
public class WalletUtils {
    public static int getIconRes(CoinType type) {
        return Constants.COINS_ICONS.get(type);
    }

    public static int getIconRes(WalletAccount account) {
        return getIconRes(account.getCoinType());
    }

    public static long longHash(@Nonnull final Sha256Hash hash) {
        return longHash(hash.getBytes());
    }

    public static long longHash(@Nonnull final byte[] bytes) {
        int len = bytes.length;
        checkState(len >= 8);

        return   (bytes[len - 1] & 0xFFl) |
                ((bytes[len - 2] & 0xFFl) << 8) |
                ((bytes[len - 3] & 0xFFl) << 16) |
                ((bytes[len - 4] & 0xFFl) << 24) |
                ((bytes[len - 5] & 0xFFl) << 32) |
                ((bytes[len - 6] & 0xFFl) << 40) |
                ((bytes[len - 7] & 0xFFl) << 48) |
                ((bytes[len - 8] & 0xFFl) << 56);
    }

    @CheckForNull
    public static List<AbstractAddress> getSendToAddress(@Nonnull final AbstractTransaction tx,
                                                         @Nonnull final AbstractWallet pocket) {
        return getToAddresses(tx, pocket, false);
    }


    @CheckForNull
    public static List<AbstractAddress> getReceivedWithOrFrom(@Nonnull final AbstractTransaction tx,
                                                              @Nonnull final AbstractWallet pocket) {
        // TODO a better approach is to use a "features" enum list and check agaist that
        if (pocket.getCoinType() instanceof NxtFamily) {
            return tx.getReceivedFrom();
        } else {
            return getToAddresses(tx, pocket, true);
        }
    }

    @CheckForNull
    private static List<AbstractAddress> getToAddresses(@Nonnull final AbstractTransaction tx,
                                                        @Nonnull final AbstractWallet pocket,
                                                        boolean toMe) {
        List<AbstractAddress> addresses = new ArrayList<>();
        List<AbstractOutput> outputs = tx.getSentTo();
        for (AbstractOutput output : outputs) {
            boolean isMine = pocket.isAddressMine(output.getAddress());
            if (isMine == toMe) {
                addresses.add(output.getAddress());
            }
        }
        return addresses;
    }

    private static final Pattern P_SIGNIFICANT = Pattern.compile("^([-+]" + Constants.CHAR_THIN_SPACE + ")?\\d*(\\.\\d{0,2})?");
    private static final Object SIGNIFICANT_SPAN = new StyleSpan(Typeface.BOLD);
    public static final RelativeSizeSpan SMALLER_SPAN = new RelativeSizeSpan(0.85f);

    public static void formatSignificant(@Nonnull final Spannable spannable, @Nullable final RelativeSizeSpan insignificantRelativeSizeSpan)
    {
        spannable.removeSpan(SIGNIFICANT_SPAN);
        if (insignificantRelativeSizeSpan != null)
            spannable.removeSpan(insignificantRelativeSizeSpan);

        final Matcher m = P_SIGNIFICANT.matcher(spannable);
        if (m.find())
        {
            final int pivot = m.group().length();
            if (pivot > 0)
                spannable.setSpan(SIGNIFICANT_SPAN, 0, pivot, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (spannable.length() > pivot && insignificantRelativeSizeSpan != null)
                spannable.setSpan(insignificantRelativeSizeSpan, pivot, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static String localeCurrencyCode() {
        try {
            return Currency.getInstance(Locale.getDefault()).getCurrencyCode();
        } catch (final IllegalArgumentException x) {
            return null;
        }
    }

    @Nullable
    public static String getCurrencyName(String code) {
        String currencyName = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                Currency currency = Currency.getInstance(code);
                currencyName = currency.getDisplayName(Locale.getDefault());
            } catch (final IllegalArgumentException x) { /* ignore */ }
        } else {
            currencyName = Currencies.CURRENCY_NAMES.get(code);
        }

        // Try cryptocurrency codes
        if (currencyName == null) {
            try {
                CoinType cryptoCurrency = CoinID.typeFromSymbol(code);
                currencyName = cryptoCurrency.getName();
            } catch (final IllegalArgumentException x) { /* ignore */ }
        }

        return currencyName;
    }
}
