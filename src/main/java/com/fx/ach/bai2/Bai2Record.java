package com.fx.ach.bai2;

import java.util.List;

/**
 * One logical BAI2 record: a physical line plus any 88 continuation lines that follow it.
 *
 * @param code   record code ("01", "02", "03", "16", "49", "98", "99", or something unexpected)
 * @param lines  the physical lines making up the record, the record line first
 * @param fields the comma-separated fields after the record code, continuations joined. For a 16
 *               record this stops before the free-form text field.
 * @param text   the free-form text of a 16 record (continuations joined with a space); "" otherwise
 */
public record Bai2Record(String code, List<Bai2Line> lines, List<String> fields, String text) {

    /** Field {@code index} (0-based, after the record code), or "" if the record ends before it. */
    public String field(int index) {
        return index < fields.size() ? fields.get(index).strip() : "";
    }

    public int lineNumber() {
        return lines.get(0).number();
    }

    /** The record exactly as it appears in the file, one line per physical record. */
    public String raw() {
        StringBuilder sb = new StringBuilder();
        for (Bai2Line line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line.text());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "line " + lineNumber() + ": " + lines.get(0).text();
    }
}
