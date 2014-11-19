package com.coinomi.core.util;


import com.coinomi.core.coins.CoinType;

import org.bitcoinj.core.Coin;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

/**
 * @author Andreas Schildbach
 * @author Giannis Dzegoutanis
 */
public class GenericUtils {
    private static final Pattern charactersO0 = Pattern.compile("[0O]");
    private static final Pattern characterIl = Pattern.compile("[Il]");
    private static final Pattern notBase58 = Pattern.compile("[^123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]");


    public static String fixAddress(final String input) {
        String fixed = charactersO0.matcher(input).replaceAll("o");
        fixed = characterIl.matcher(fixed).replaceAll("1");
        fixed = notBase58.matcher(fixed).replaceAll("");
        return fixed;
    }

    public static String addressSplitToGroupsMultiline(final String address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address.substring(0, 4));
        sb.append(" ");
        sb.append(address.substring(4, 8));
        sb.append(" ");
        sb.append(address.substring(8, 12));
        sb.append(" ");
        sb.append(address.substring(12, 17));
        sb.append("\n");
        sb.append(address.substring(17, 21));
        sb.append(" ");
        sb.append(address.substring(21, 25));
        sb.append(" ");
        sb.append(address.substring(25, 29));
        sb.append(" ");
        sb.append(address.substring(29));

        return sb.toString();
    }

    public static String addressSplitToGroups(final String address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address.substring(0, 5));
        sb.append(" ");
        sb.append(address.substring(5, 9));
        sb.append(" ");
        sb.append(address.substring(9, 13));
        sb.append(" ");
        sb.append(address.substring(13, 17));
        sb.append(" ");
        sb.append(address.substring(17, 21));
        sb.append(" ");
        sb.append(address.substring(21, 25));
        sb.append(" ");
        sb.append(address.substring(25, 29));
        sb.append(" ");
        sb.append(address.substring(29));

        return sb.toString();
    }

    public static Coin parseCoin(final CoinType type, final String str) throws IllegalArgumentException {
        long units = new BigDecimal(str).movePointRight(type.getUnitExponent()).toBigIntegerExact().longValue();
        return Coin.valueOf(units);
    }

    public static String formatValue(@Nonnull final CoinType type, @Nonnull final Coin value) {
        return formatValue(type, value, "", "-", 8, 0);
    }

    public static String formatValue(@Nonnull final CoinType type, @Nonnull final Coin value,
                                     final int precision, final int shift) {
        return formatValue(type, value, "", "-", precision, shift);
    }

    public static String formatValue(@Nonnull final CoinType type, @Nonnull final Coin value,
                                     @Nonnull final String plusSign, @Nonnull final String minusSign,
                                     final int precision, final int shift) {
        long longValue = value.longValue();

        final String sign = value.isNegative() ? minusSign : plusSign;

        String formatedValue;

        if (shift == 0) {
            long units = (long) Math.pow(10, type.getUnitExponent());
            long precisionUnits = (long) (units / Math.pow(10, precision));
            long roundingPrecisionUnits = precisionUnits / 2;

            if (precision == 2 || precision == 4 || precision == 6 || precision == 8) {
                if (roundingPrecisionUnits > 0) {
                    longValue = longValue - longValue % precisionUnits + longValue % precisionUnits / roundingPrecisionUnits * precisionUnits;
                }
            } else {
                throw new IllegalArgumentException("cannot handle precision/shift: " + precision + "/" + shift);
            }

            final long absValue = Math.abs(longValue);
            final long coins = absValue / units;
            final int satoshis = (int) (absValue % units);

            if (satoshis % (units / 100) == 0)
                formatedValue = String.format(Locale.US, "%d.%02d", coins, satoshis / (units / 100));
            else if (satoshis % (units / 10000) == 0)
                formatedValue = String.format(Locale.US, "%d.%04d", coins, satoshis / (units / 10000));
            else if (satoshis % (units / 1000000) == 0)
                formatedValue = String.format(Locale.US, "%d.%06d", coins, satoshis / (units / 1000000));
            else
                formatedValue = String.format(Locale.US, "%d.%08d", coins, satoshis);
//        } else if (shift == 3) {
//            if (precision == 2)
//                longValue = longValue - longValue % 1000 + longValue % 1000 / 500 * 1000;
//            else if (precision == 4)
//                longValue = longValue - longValue % 10 + longValue % 10 / 5 * 10;
//            else if (precision == 5)
//                ;
//            else
//                throw new IllegalArgumentException("cannot handle precision/shift: " + precision + "/" + shift);
//
//            final long absValue = Math.abs(longValue);
//            final long coins = absValue / ONE_MBTC_INT;
//            final long satoshis = (int) (absValue % ONE_MBTC_INT);
//
//            if (satoshis % 1000 == 0)
//                formatedValue = String.format(Locale.US, "%d.%02d", sign, coins, satoshis / 1000);
//            else if (satoshis % 10 == 0)
//                formatedValue = String.format(Locale.US, "%d.%04d", sign, coins, satoshis / 10);
//            else
//                formatedValue = String.format(Locale.US, "%d.%05d", sign, coins, satoshis);
//        } else if (shift == 6) {
//            if (precision == 0)
//                longValue = longValue - longValue % 100 + longValue % 100 / 50 * 100;
//            else if (precision == 2)
//                ;
//            else
//                throw new IllegalArgumentException("cannot handle precision/shift: " + precision + "/" + shift);
//
//            final long absValue = Math.abs(longValue);
//            final long coins = absValue / ONE_UBTC_INT;
//            final long satoshis = (int) (absValue % ONE_UBTC_INT);
//
//            if (satoshis % 100 == 0)
//                formatedValue = String.format(Locale.US, "%d", sign, coins);
//            else
//                formatedValue = String.format(Locale.US, "%d.%02d", sign, coins, satoshis);
        } else {
            throw new IllegalArgumentException("cannot handle shift: " + shift);
        }

        // Relax precision if incorrectly shows value as 0.00
        if (formatedValue.equals("0.00") && !value.isZero()) {
            return formatValue(type, value, plusSign, minusSign, precision + 2, shift);
        }

        // Add the sign if needed
        formatedValue = String.format(Locale.US, "%s%s", sign, formatedValue);

        return formatedValue;
    }
}
