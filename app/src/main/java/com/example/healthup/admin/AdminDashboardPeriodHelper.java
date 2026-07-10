package com.example.healthup.admin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class AdminDashboardPeriodHelper {

    public static final String TODAY = "today";
    public static final String YESTERDAY = "yesterday";
    public static final String LAST_7_DAYS = "last_7_days";
    public static final String THIS_WEEK = "this_week";
    public static final String LAST_WEEK = "last_week";
    public static final String THIS_MONTH = "this_month";
    public static final String LAST_MONTH = "last_month";
    public static final String THIS_YEAR = "this_year";

    public static final List<String> PRESET_KEYS = Arrays.asList(
            TODAY,
            YESTERDAY,
            LAST_7_DAYS,
            THIS_WEEK,
            LAST_WEEK,
            THIS_MONTH,
            LAST_MONTH,
            THIS_YEAR
    );

    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

    private AdminDashboardPeriodHelper() {
    }

    public static class DateRange {
        public final long startMs;
        public final long endMs;
        public final String key;

        public DateRange(@NonNull String key, long startMs, long endMs) {
            this.key = key;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        public boolean contains(long timeMs) {
            return timeMs >= startMs && timeMs < endMs;
        }
    }

    @NonNull
    public static DateRange resolve(@NonNull String key) {
        return resolve(key, Calendar.getInstance(TZ));
    }

    @NonNull
    public static DateRange resolve(@NonNull String key, @NonNull Calendar now) {
        Calendar start = (Calendar) now.clone();
        Calendar end = (Calendar) now.clone();

        switch (key) {
            case YESTERDAY:
                start.add(Calendar.DAY_OF_YEAR, -1);
                setStartOfDay(start);
                end.add(Calendar.DAY_OF_YEAR, -1);
                setEndOfDay(end);
                break;
            case LAST_7_DAYS:
                start.add(Calendar.DAY_OF_YEAR, -6);
                setStartOfDay(start);
                setEndOfDay(end);
                break;
            case THIS_WEEK:
                moveToWeekStart(start);
                setStartOfDay(start);
                setEndOfDay(end);
                break;
            case LAST_WEEK:
                moveToWeekStart(start);
                start.add(Calendar.WEEK_OF_YEAR, -1);
                setStartOfDay(start);
                end.setTimeInMillis(start.getTimeInMillis());
                end.add(Calendar.DAY_OF_YEAR, 6);
                setEndOfDay(end);
                break;
            case THIS_MONTH:
                start.set(Calendar.DAY_OF_MONTH, 1);
                setStartOfDay(start);
                setEndOfDay(end);
                break;
            case LAST_MONTH:
                start.add(Calendar.MONTH, -1);
                start.set(Calendar.DAY_OF_MONTH, 1);
                setStartOfDay(start);
                end.set(Calendar.DAY_OF_MONTH, 1);
                end.add(Calendar.DAY_OF_YEAR, -1);
                setEndOfDay(end);
                break;
            case THIS_YEAR:
                start.set(Calendar.DAY_OF_YEAR, 1);
                setStartOfDay(start);
                setEndOfDay(end);
                break;
            case TODAY:
            default:
                setStartOfDay(start);
                setEndOfDay(end);
                break;
        }

        return new DateRange(key, start.getTimeInMillis(), end.getTimeInMillis() + 1);
    }

    @NonNull
    public static String defaultCompareKey(@NonNull String primaryKey) {
        switch (primaryKey) {
            case TODAY:
                return YESTERDAY;
            case YESTERDAY:
                return LAST_7_DAYS;
            case LAST_7_DAYS:
                return LAST_WEEK;
            case THIS_WEEK:
                return LAST_WEEK;
            case THIS_MONTH:
                return LAST_MONTH;
            case THIS_YEAR:
                return LAST_MONTH;
            case LAST_WEEK:
            case LAST_MONTH:
            default:
                return YESTERDAY;
        }
    }

    @NonNull
    public static String formatRangeLabel(@NonNull String key, @NonNull DateRange range) {
        java.text.SimpleDateFormat dayFormat = new java.text.SimpleDateFormat("dd/MM", Locale.getDefault());
        dayFormat.setTimeZone(TZ);
        if (TODAY.equals(key) || YESTERDAY.equals(key)) {
            return dayFormat.format(range.startMs);
        }
        return dayFormat.format(range.startMs) + " – " + dayFormat.format(range.endMs - 1);
    }

    private static void moveToWeekStart(@NonNull Calendar calendar) {
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        int delta = day - Calendar.MONDAY;
        if (delta < 0) {
            delta += 7;
        }
        calendar.add(Calendar.DAY_OF_YEAR, -delta);
    }

    private static void setStartOfDay(@NonNull Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private static void setEndOfDay(@NonNull Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
    }
}
