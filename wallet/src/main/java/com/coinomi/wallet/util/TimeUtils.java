package com.coinomi.wallet.util;

import android.content.Context;
import android.text.format.DateUtils;
import android.text.format.Time;

/**
 * @author John L. Jegutanis
 */
public class TimeUtils {
    public static final long TIME_PRECISION = DateUtils.MINUTE_IN_MILLIS;

    private static Time nowTime = new Time();
    private static Time thenTime = new Time();

    /**
     * Show time in a human friendly format
     * @param seconds timestamp in seconds since epoch
     */
    public static CharSequence toRelativeTimeString(long seconds) {
        long now = System.currentTimeMillis();
        long millis = seconds * DateUtils.SECOND_IN_MILLIS;
        synchronized (TimeUtils.class) {
            nowTime.set(now);
            thenTime.set(millis);

            if (nowTime.year == thenTime.year) {
                return DateUtils.getRelativeTimeSpanString(millis, now, TIME_PRECISION, 0);
            } else {
                return DateUtils.getRelativeTimeSpanString(millis, now, TIME_PRECISION,
                        DateUtils.FORMAT_ABBREV_ALL);
            }
        }
    }

    /**
     * Show time in a human friendly format
     * @param seconds timestamp in seconds since epoch
     */
    public static CharSequence toTimeString(Context context, long seconds) {
        return DateUtils.formatDateTime(context, seconds * DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE);
    }
}
