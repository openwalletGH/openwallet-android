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


import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.wallet.Constants;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.WalletProtobufSerializer;

/**
 * @author Andreas Schildbach
 */
public class WalletUtils {

    public static long longHash(@Nonnull final Sha256Hash hash) {
        final byte[] bytes = hash.getBytes();

        return (bytes[31] & 0xFFl) | ((bytes[30] & 0xFFl) << 8) | ((bytes[29] & 0xFFl) << 16) | ((bytes[28] & 0xFFl) << 24)
                | ((bytes[27] & 0xFFl) << 32) | ((bytes[26] & 0xFFl) << 40) | ((bytes[25] & 0xFFl) << 48) | ((bytes[23] & 0xFFl) << 56);
    }

    public static boolean isInternal(@Nonnull final Transaction tx) {
        if (tx.isCoinBase())
            return false;

        final List<TransactionOutput> outputs = tx.getOutputs();
        if (outputs.size() != 1)
            return false;

        try
        {
            final TransactionOutput output = outputs.get(0);
            final Script scriptPubKey = output.getScriptPubKey();
            if (!scriptPubKey.isSentToRawPubKey())
                return false;

            return true;
        }
        catch (final ScriptException x)
        {
            return false;
        }
    }

    @CheckForNull
    public static Address getSendToAddress(@Nonnull final Transaction tx, @Nonnull final WalletPocket pocket) {
        return getToAddress(tx, pocket, false);
    }


    @CheckForNull
    public static Address getReceivedWithAddress(@Nonnull final Transaction tx, @Nonnull final WalletPocket pocket) {
        return getToAddress(tx, pocket, true);
    }

    @CheckForNull
    private static Address getToAddress(@Nonnull final Transaction tx,
                                       @Nonnull final WalletPocket pocket, boolean toMe) {
        try {
            for (final TransactionOutput output : tx.getOutputs()) {
                if (output.isMine(pocket) == toMe) {
                    return output.getScriptPubKey().getToAddress(pocket.getCoinType());
                }
            }

            throw new IllegalStateException();
        } catch (final ScriptException x) {
            return null;
        }
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
}
