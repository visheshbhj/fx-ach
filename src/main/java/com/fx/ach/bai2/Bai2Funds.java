package com.fx.ach.bai2;

import java.util.ArrayList;
import java.util.List;

/**
 * Funds availability of an amount: the funds type code plus the extra fields it brings along
 * (none for Z/0/1/2, three amounts for S, value date and time for V, a distribution list for D).
 */
public record Bai2Funds(String type, List<String> values) {

    /** Names of the extra fields, matching {@link #values()} one for one. */
    public List<String> valueNames() {
        List<String> names = new ArrayList<>();
        switch (type) {
            case "S" -> names.addAll(List.of("Immediate availability amount", "One-day availability amount",
                    "Two-or-more-day availability amount"));
            case "V" -> names.addAll(List.of("Value date", "Value time"));
            case "D" -> {
                names.add("Number of distributions");
                for (int i = 1; names.size() < values.size(); i++) {
                    names.add("Distribution " + i + ": days");
                    names.add("Distribution " + i + ": amount");
                }
            }
            default -> {
            }
        }
        return names.subList(0, Math.min(names.size(), values.size()));
    }

    /** Plain-English availability, e.g. "Value dated Sep 30, 2026 16:00". */
    public String describe(String currency) {
        String base = Bai2Codes.FUNDS_TYPE.getOrDefault(type, "Unknown funds type \"" + type + "\"");
        return switch (type) {
            case "S" -> "Immediate " + Bai2Format.money(value(0), currency) + ", one-day " + Bai2Format.money(value(1), currency)
                    + ", two+ days " + Bai2Format.money(value(2), currency);
            case "V" -> "Value dated " + Bai2Format.readableDate(value(0))
                    + (value(1).isEmpty() ? "" : " " + Bai2Format.readableTime(value(1)));
            case "D" -> {
                StringBuilder sb = new StringBuilder("Distributed:");
                for (int i = 1; i + 1 < values.size(); i += 2) {
                    sb.append(i > 1 ? "," : "").append(' ').append(Bai2Format.money(value(i + 1), currency))
                            .append(" in ").append(value(i)).append(" day(s)");
                }
                yield sb.toString();
            }
            default -> base;
        };
    }

    String value(int i) {
        return i < values.size() ? values.get(i).strip() : "";
    }
}
