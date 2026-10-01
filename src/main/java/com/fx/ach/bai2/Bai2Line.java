package com.fx.ach.bai2;

/**
 * One physical line of a BAI2 file, as it appears in the file (trailing padding removed).
 * {@code number} is the 1-based line number in the original text.
 */
public record Bai2Line(int number, String text) {

    /** The two-digit record code before the first comma, e.g. "16" or "88". */
    public String code() {
        int end = 0;
        while (end < text.length() && text.charAt(end) != ',' && text.charAt(end) != '/') {
            end++;
        }
        return text.substring(0, end).strip();
    }

    /** Everything after the record code and its comma. */
    public String body() {
        int comma = text.indexOf(',');
        return comma < 0 ? "" : text.substring(comma + 1);
    }
}
