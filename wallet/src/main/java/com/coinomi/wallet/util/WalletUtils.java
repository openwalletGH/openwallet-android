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
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.wallet.Constants;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
        } else if (currencyNamesCompat != null) {
            currencyName = currencyNamesCompat.get(code);
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

    private static final HashMap<String, String> currencyNamesCompat;

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            currencyNamesCompat = new HashMap<String, String>(164);
            currencyNamesCompat.put("AED", "United Arab Emirates Dirham");
            currencyNamesCompat.put("AFN", "Afghan Afghani");
            currencyNamesCompat.put("ALL", "Albanian Lek");
            currencyNamesCompat.put("AMD", "Armenian Dram");
            currencyNamesCompat.put("ANG", "Netherlands Antillean Guilder");
            currencyNamesCompat.put("AOA", "Angolan Kwanza");
            currencyNamesCompat.put("ARS", "Argentine Peso");
            currencyNamesCompat.put("AUD", "Australian Dollar");
            currencyNamesCompat.put("AWG", "Aruban Florin");
            currencyNamesCompat.put("AZN", "Azerbaijani Manat");
            currencyNamesCompat.put("BAM", "Bosnia-Herzegovina Convertible Mark");
            currencyNamesCompat.put("BBD", "Barbadian Dollar");
            currencyNamesCompat.put("BDT", "Bangladeshi Taka");
            currencyNamesCompat.put("BGN", "Bulgarian Lev");
            currencyNamesCompat.put("BHD", "Bahraini Dinar");
            currencyNamesCompat.put("BIF", "Burundian Franc");
            currencyNamesCompat.put("BMD", "Bermudan Dollar");
            currencyNamesCompat.put("BND", "Brunei Dollar");
            currencyNamesCompat.put("BOB", "Bolivian Boliviano");
            currencyNamesCompat.put("BRL", "Brazilian Real");
            currencyNamesCompat.put("BSD", "Bahamian Dollar");
            currencyNamesCompat.put("BTN", "Bhutanese Ngultrum");
            currencyNamesCompat.put("BWP", "Botswanan Pula");
            currencyNamesCompat.put("BYR", "Belarusian Ruble");
            currencyNamesCompat.put("BZD", "Belize Dollar");
            currencyNamesCompat.put("CAD", "Canadian Dollar");
            currencyNamesCompat.put("CDF", "Congolese Franc");
            currencyNamesCompat.put("CHF", "Swiss Franc");
            currencyNamesCompat.put("CLF", "Chilean Unit of Account (UF)");
            currencyNamesCompat.put("CLP", "Chilean Peso");
            currencyNamesCompat.put("CNY", "Chinese Yuan");
            currencyNamesCompat.put("COP", "Colombian Peso");
            currencyNamesCompat.put("CRC", "Costa Rican Colón");
            currencyNamesCompat.put("CUP", "Cuban Peso");
            currencyNamesCompat.put("CVE", "Cape Verdean Escudo");
            currencyNamesCompat.put("CZK", "Czech Republic Koruna");
            currencyNamesCompat.put("DJF", "Djiboutian Franc");
            currencyNamesCompat.put("DKK", "Danish Krone");
            currencyNamesCompat.put("DOP", "Dominican Peso");
            currencyNamesCompat.put("DZD", "Algerian Dinar");
            currencyNamesCompat.put("EEK", "Estonian Kroon");
            currencyNamesCompat.put("EGP", "Egyptian Pound");
            currencyNamesCompat.put("ERN", "Eritrean Nakfa");
            currencyNamesCompat.put("ETB", "Ethiopian Birr");
            currencyNamesCompat.put("EUR", "Euro");
            currencyNamesCompat.put("FJD", "Fijian Dollar");
            currencyNamesCompat.put("FKP", "Falkland Islands Pound");
            currencyNamesCompat.put("GBP", "British Pound Sterling");
            currencyNamesCompat.put("GEL", "Georgian Lari");
            currencyNamesCompat.put("GHS", "Ghanaian Cedi");
            currencyNamesCompat.put("GIP", "Gibraltar Pound");
            currencyNamesCompat.put("GMD", "Gambian Dalasi");
            currencyNamesCompat.put("GNF", "Guinean Franc");
            currencyNamesCompat.put("GTQ", "Guatemalan Quetzal");
            currencyNamesCompat.put("GYD", "Guyanaese Dollar");
            currencyNamesCompat.put("HKD", "Hong Kong Dollar");
            currencyNamesCompat.put("HNL", "Honduran Lempira");
            currencyNamesCompat.put("HRK", "Croatian Kuna");
            currencyNamesCompat.put("HTG", "Haitian Gourde");
            currencyNamesCompat.put("HUF", "Hungarian Forint");
            currencyNamesCompat.put("IDR", "Indonesian Rupiah");
            currencyNamesCompat.put("ILS", "Israeli New Sheqel");
            currencyNamesCompat.put("INR", "Indian Rupee");
            currencyNamesCompat.put("IQD", "Iraqi Dinar");
            currencyNamesCompat.put("IRR", "Iranian Rial");
            currencyNamesCompat.put("ISK", "Icelandic Króna");
            currencyNamesCompat.put("JMD", "Jamaican Dollar");
            currencyNamesCompat.put("JOD", "Jordanian Dinar");
            currencyNamesCompat.put("JPY", "Japanese Yen");
            currencyNamesCompat.put("KES", "Kenyan Shilling");
            currencyNamesCompat.put("KGS", "Kyrgystani Som");
            currencyNamesCompat.put("KHR", "Cambodian Riel");
            currencyNamesCompat.put("KMF", "Comorian Franc");
            currencyNamesCompat.put("KPW", "North Korean Won");
            currencyNamesCompat.put("KRW", "South Korean Won");
            currencyNamesCompat.put("KWD", "Kuwaiti Dinar");
            currencyNamesCompat.put("KYD", "Cayman Islands Dollar");
            currencyNamesCompat.put("KZT", "Kazakhstani Tenge");
            currencyNamesCompat.put("LAK", "Laotian Kip");
            currencyNamesCompat.put("LBP", "Lebanese Pound");
            currencyNamesCompat.put("LKR", "Sri Lankan Rupee");
            currencyNamesCompat.put("LRD", "Liberian Dollar");
            currencyNamesCompat.put("LSL", "Lesotho Loti");
            currencyNamesCompat.put("LTL", "Lithuanian Litas");
            currencyNamesCompat.put("LVL", "Latvian Lats");
            currencyNamesCompat.put("LYD", "Libyan Dinar");
            currencyNamesCompat.put("MAD", "Moroccan Dirham");
            currencyNamesCompat.put("MDL", "Moldovan Leu");
            currencyNamesCompat.put("MGA", "Malagasy Ariary");
            currencyNamesCompat.put("MKD", "Macedonian Denar");
            currencyNamesCompat.put("MMK", "Myanmar Kyat");
            currencyNamesCompat.put("MNT", "Mongolian Tugrik");
            currencyNamesCompat.put("MOP", "Macanese Pataca");
            currencyNamesCompat.put("MRO", "Mauritanian Ouguiya");
            currencyNamesCompat.put("MTL", "Maltese Lira");
            currencyNamesCompat.put("MUR", "Mauritian Rupee");
            currencyNamesCompat.put("MVR", "Maldivian Rufiyaa");
            currencyNamesCompat.put("MWK", "Malawian Kwacha");
            currencyNamesCompat.put("MXN", "Mexican Peso");
            currencyNamesCompat.put("MYR", "Malaysian Ringgit");
            currencyNamesCompat.put("MZN", "Mozambican Metical");
            currencyNamesCompat.put("NAD", "Namibian Dollar");
            currencyNamesCompat.put("NGN", "Nigerian Naira");
            currencyNamesCompat.put("NIO", "Nicaraguan Córdoba");
            currencyNamesCompat.put("NOK", "Norwegian Krone");
            currencyNamesCompat.put("NPR", "Nepalese Rupee");
            currencyNamesCompat.put("NZD", "New Zealand Dollar");
            currencyNamesCompat.put("OMR", "Omani Rial");
            currencyNamesCompat.put("PAB", "Panamanian Balboa");
            currencyNamesCompat.put("PEN", "Peruvian Nuevo Sol");
            currencyNamesCompat.put("PGK", "Papua New Guinean Kina");
            currencyNamesCompat.put("PHP", "Philippine Peso");
            currencyNamesCompat.put("PKR", "Pakistani Rupee");
            currencyNamesCompat.put("PLN", "Polish Zloty");
            currencyNamesCompat.put("PYG", "Paraguayan Guarani");
            currencyNamesCompat.put("QAR", "Qatari Rial");
            currencyNamesCompat.put("RON", "Romanian Leu");
            currencyNamesCompat.put("RSD", "Serbian Dinar");
            currencyNamesCompat.put("RUB", "Russian Ruble");
            currencyNamesCompat.put("RWF", "Rwandan Franc");
            currencyNamesCompat.put("SAR", "Saudi Riyal");
            currencyNamesCompat.put("SBD", "Solomon Islands Dollar");
            currencyNamesCompat.put("SCR", "Seychellois Rupee");
            currencyNamesCompat.put("SDG", "Sudanese Pound");
            currencyNamesCompat.put("SEK", "Swedish Krona");
            currencyNamesCompat.put("SGD", "Singapore Dollar");
            currencyNamesCompat.put("SHP", "Saint Helena Pound");
            currencyNamesCompat.put("SLL", "Sierra Leonean Leone");
            currencyNamesCompat.put("SOS", "Somali Shilling");
            currencyNamesCompat.put("SRD", "Surinamese Dollar");
            currencyNamesCompat.put("STD", "São Tomé and Príncipe Dobra");
            currencyNamesCompat.put("SVC", "Salvadoran Colón");
            currencyNamesCompat.put("SYP", "Syrian Pound");
            currencyNamesCompat.put("SZL", "Swazi Lilangeni");
            currencyNamesCompat.put("THB", "Thai Baht");
            currencyNamesCompat.put("TJS", "Tajikistani Somoni");
            currencyNamesCompat.put("TMT", "Turkmenistani Manat");
            currencyNamesCompat.put("TND", "Tunisian Dinar");
            currencyNamesCompat.put("TOP", "Tongan Paʻanga");
            currencyNamesCompat.put("TRY", "Turkish Lira");
            currencyNamesCompat.put("TTD", "Trinidad and Tobago Dollar");
            currencyNamesCompat.put("TWD", "New Taiwan Dollar");
            currencyNamesCompat.put("TZS", "Tanzanian Shilling");
            currencyNamesCompat.put("UAH", "Ukrainian Hryvnia");
            currencyNamesCompat.put("UGX", "Ugandan Shilling");
            currencyNamesCompat.put("USD", "US Dollar");
            currencyNamesCompat.put("UYU", "Uruguayan Peso");
            currencyNamesCompat.put("UZS", "Uzbekistan Som");
            currencyNamesCompat.put("VEF", "Venezuelan Bolívar");
            currencyNamesCompat.put("VND", "Vietnamese Dong");
            currencyNamesCompat.put("VUV", "Vanuatu Vatu");
            currencyNamesCompat.put("WST", "Samoan Tala");
            currencyNamesCompat.put("XAF", "CFA Franc BEAC");
            currencyNamesCompat.put("XAG", "Silver");
            currencyNamesCompat.put("XAU", "Gold");
            currencyNamesCompat.put("XCD", "East Caribbean Dollar");
            currencyNamesCompat.put("XDR", "Special Drawing Rights");
            currencyNamesCompat.put("XOF", "CFA Franc BCEAO");
            currencyNamesCompat.put("XPF", "CFP Franc");
            currencyNamesCompat.put("YER", "Yemeni Rial");
            currencyNamesCompat.put("ZAR", "South African Rand");
            currencyNamesCompat.put("ZMK", "Zambian Kwacha (1968–2012)");
            currencyNamesCompat.put("ZMW", "Zambian Kwacha");
            currencyNamesCompat.put("ZWL", "Zimbabwean Dollar (2009)");
        } else {
            currencyNamesCompat = null;
        }
    }
}
