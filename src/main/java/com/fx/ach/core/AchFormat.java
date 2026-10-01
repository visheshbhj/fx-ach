package com.fx.ach.core;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;

/**
 * Formatting helpers that turn raw fixed-width values into something a person can read.
 */
public final class AchFormat {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter READABLE_DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US);

    private AchFormat() {
    }

    public static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount == null ? BigDecimal.ZERO : amount);
    }

    /** Parses an implied-decimal amount such as "0000012550" into 125.50. */
    public static BigDecimal impliedCents(String raw) {
        String digits = raw == null ? "" : raw.trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return new BigDecimal(digits).movePointLeft(2);
    }

    public static String yymmdd(String raw) {
        LocalDate date = parseYymmdd(raw);
        return date == null ? "" : READABLE_DATE.format(date);
    }

    public static LocalDate parseYymmdd(String raw) {
        if (raw == null || raw.trim().length() != 6) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), YYMMDD);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String readable(LocalDate date) {
        return date == null ? "" : READABLE_DATE.format(date);
    }

    public static String readable(Date date) {
        return date == null ? "" : readable(toLocalDate(date));
    }

    public static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static Date toDate(LocalDate date) {
        return date == null ? null : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public static String hhmm(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() != 4 || !t.chars().allMatch(Character::isDigit)) {
            return "";
        }
        return t.substring(0, 2) + ":" + t.substring(2);
    }

    /** Next weekday after today; a sensible default effective entry date. */
    public static LocalDate nextBusinessDay(LocalDate from) {
        LocalDate d = from.plusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** Masks all but the last four characters of an account number. */
    public static String maskAccount(String account) {
        String a = account == null ? "" : account.trim();
        if (a.length() <= 4) {
            return a;
        }
        return "•".repeat(a.length() - 4) + a.substring(a.length() - 4);
    }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    // ---- ABA routing numbers ------------------------------------------------------------

    private static final int[] ABA_WEIGHTS = {3, 7, 1, 3, 7, 1, 3, 7};

    /** Computes the ABA check digit for the first 8 digits of a routing number. */
    public static int routingCheckDigit(String first8) {
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += Character.digit(first8.charAt(i), 10) * ABA_WEIGHTS[i];
        }
        return (10 - sum % 10) % 10;
    }

    public static boolean isValidRouting(String routing) {
        String r = routing == null ? "" : routing.trim();
        if (r.length() != 9 || !r.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return routingCheckDigit(r) == Character.digit(r.charAt(8), 10);
    }

    /** Describes a 9-digit routing number (or a 10-char immediate origin/destination field). */
    public static String describeRouting(String raw) {
        String r = trim(raw);
        if (r.length() == 9 && r.chars().allMatch(Character::isDigit)) {
            return "Routing " + r + (isValidRouting(r) ? " (check digit OK)" : " (check digit INVALID)");
        }
        return r;
    }
}
