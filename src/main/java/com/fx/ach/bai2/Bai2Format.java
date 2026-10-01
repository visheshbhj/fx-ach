package com.fx.ach.bai2;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

/** Parsing and display of BAI2 amounts, dates and times. */
public final class Bai2Format {

    private static final Pattern AMOUNT = Pattern.compile("[+-]?\\d{1,18}");
    private static final DateTimeFormatter READABLE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private Bai2Format() {
    }

    /** Parses an amount in minor units ("+12550" → 12550); null if blank or not a number. */
    public static Long parseAmount(String raw) {
        String s = raw == null ? "" : raw.strip();
        return AMOUNT.matcher(s).matches() ? Long.parseLong(s.startsWith("+") ? s.substring(1) : s) : null;
    }

    public static boolean isAmount(String raw) {
        return parseAmount(raw) != null;
    }

    /** Decimal places implied by a currency code: 2 for USD, 0 for JPY, 3 for KWD; 2 if unknown. */
    public static int decimals(String currency) {
        try {
            int d = Currency.getInstance(currency).getDefaultFractionDigits();
            return d < 0 ? 2 : d;
        } catch (IllegalArgumentException | NullPointerException e) {
            return 2;
        }
    }

    /** Formats minor units as money in the given currency, e.g. 12550 USD → "$125.50". */
    public static String money(long minor, String currency) {
        int decimals = decimals(currency);
        BigDecimal value = BigDecimal.valueOf(minor, decimals);
        try {
            NumberFormat f = NumberFormat.getCurrencyInstance(Locale.US);
            f.setCurrency(Currency.getInstance(currency));
            f.setMinimumFractionDigits(decimals);
            f.setMaximumFractionDigits(decimals);
            return f.format(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            NumberFormat f = NumberFormat.getNumberInstance(Locale.US);
            f.setMinimumFractionDigits(decimals);
            f.setMaximumFractionDigits(decimals);
            return f.format(value) + (currency == null || currency.isBlank() ? "" : " " + currency);
        }
    }

    /** Formats a raw amount field; blank stays blank, and a non-number is returned as typed. */
    public static String money(String raw, String currency) {
        Long minor = parseAmount(raw);
        return minor == null ? (raw == null ? "" : raw.strip()) : money(minor, currency);
    }

    /** Parses YYMMDD (years 70–99 are 19xx, the rest 20xx); null if not a real date. */
    public static LocalDate parseDate(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (!s.matches("\\d{6}")) {
            return null;
        }
        int yy = Integer.parseInt(s.substring(0, 2));
        try {
            return LocalDate.of(yy >= 70 ? 1900 + yy : 2000 + yy, Integer.parseInt(s.substring(2, 4)), Integer.parseInt(s.substring(4, 6)));
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    public static String readableDate(String raw) {
        LocalDate d = parseDate(raw);
        return d == null ? (raw == null || raw.isBlank() ? "(no date)" : "\"" + raw.strip() + "\" (not a valid YYMMDD date)") : READABLE.format(d);
    }

    /** HHMM, 0000–2359, plus 2400 and 9999 which BAI2 uses for "end of day". */
    public static boolean isTime(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.equals("9999") || s.equals("2400")) {
            return true;
        }
        return s.matches("\\d{4}") && Integer.parseInt(s.substring(0, 2)) < 24 && Integer.parseInt(s.substring(2)) < 60;
    }

    public static String readableTime(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.equals("9999") || s.equals("2400")) {
            return "end of day";
        }
        return isTime(s) ? s.substring(0, 2) + ":" + s.substring(2) : "\"" + s + "\"";
    }
}
