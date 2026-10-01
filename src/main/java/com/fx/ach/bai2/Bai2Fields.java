package com.fx.ach.bai2;

import java.util.ArrayList;
import java.util.List;

/** Names and plain-English meanings of every field in a BAI2 record, for display. */
public final class Bai2Fields {

    private Bai2Fields() {
    }

    /** One field of a record: its name, value as it appears in the file, and what it means. */
    public record Field(String name, String value, String meaning) {
    }

    /** A piece of one physical line, shaded as one field in the raw view. {@code field} is -1 for the record code. */
    public record Segment(String text, int field, String name) {
    }

    /** The record's fields in file order, after the record code. */
    public static List<Field> fields(Bai2File file, Bai2Record r) {
        String cur = file.currencyOf(r);
        List<Field> out = new ArrayList<>();
        switch (r.code()) {
            case "01" -> {
                add(out, r, 0, "Sender identification", "Who sent the file (usually the bank)");
                add(out, r, 1, "Receiver identification", "Who the file is for");
                add(out, r, 2, "File creation date", Bai2Format.readableDate(r.field(2)));
                add(out, r, 3, "File creation time", r.field(3).isEmpty() ? "" : Bai2Format.readableTime(r.field(3)));
                add(out, r, 4, "File identification number", "Unique per sender, receiver and creation date");
                add(out, r, 5, "Physical record length", r.field(5).isEmpty() ? "Not given (variable length)" : r.field(5) + " characters per line");
                add(out, r, 6, "Block size", r.field(6).isEmpty() ? "Not given" : r.field(6) + " records per block");
                add(out, r, 7, "Version number", r.field(7).equals("2") ? "BAI version 2" : "Expected 2 (BAI2)");
            }
            case "02" -> {
                add(out, r, 0, "Ultimate receiver identification", "Who the data in this group is for");
                add(out, r, 1, "Originator identification", "The bank reporting these accounts");
                add(out, r, 2, "Group status", Bai2Codes.describe(Bai2Codes.GROUP_STATUS, r.field(2)));
                add(out, r, 3, "As-of date", Bai2Format.readableDate(r.field(3)));
                add(out, r, 4, "As-of time", r.field(4).isEmpty() ? "Not given" : Bai2Format.readableTime(r.field(4)));
                add(out, r, 5, "Currency code", r.field(5).isEmpty() ? "Not given (USD assumed)" : r.field(5));
                add(out, r, 6, "As-of date modifier", r.field(6).isEmpty() ? "Not given" : Bai2Codes.describe(Bai2Codes.AS_OF_MODIFIER, r.field(6)));
            }
            case "03" -> {
                add(out, r, 0, "Customer account number", "");
                add(out, r, 1, "Currency code", r.field(1).isEmpty() ? "Not given; the group's currency (" + cur + ") applies" : r.field(1));
                int i = 2;
                int n = 0;
                while (i < r.fields().size()) {
                    n++;
                    String type = r.field(i);
                    String funds = r.field(i + 3);
                    add(out, r, i, "Type code #" + n, type.isEmpty() ? "" : Bai2Codes.typeName(type) + " (" + Bai2Codes.kind(type).label.toLowerCase() + ")");
                    add(out, r, i + 1, "Amount #" + n, Bai2Format.money(r.field(i + 1), cur));
                    add(out, r, i + 2, "Item count #" + n, r.field(i + 2).isEmpty() ? "" : r.field(i + 2) + " item(s)");
                    add(out, r, i + 3, "Funds type #" + n, funds.isEmpty() ? "" : Bai2Codes.describe(Bai2Codes.FUNDS_TYPE, funds));
                    i += 4;
                    i = fundsExtras(out, r, funds, i, cur, " #" + n);
                }
            }
            case "16" -> {
                String type = r.field(0);
                add(out, r, 0, "Type code", Bai2Codes.typeName(type) + " (" + Bai2Codes.kind(type).label.toLowerCase() + ")");
                add(out, r, 1, "Amount", Bai2Format.money(r.field(1), cur));
                add(out, r, 2, "Funds type", Bai2Codes.describe(Bai2Codes.FUNDS_TYPE, r.field(2)));
                int i = fundsExtras(out, r, r.field(2), 3, cur, "");
                add(out, r, i, "Bank reference number", "");
                add(out, r, i + 1, "Customer reference number", "");
                out.add(new Field("Text", r.text(), ""));
            }
            case "49" -> {
                add(out, r, 0, "Account control total", Bai2Format.money(r.field(0), cur) + " · sum of the account's 03 and 16 amounts");
                add(out, r, 1, "Number of records", "Records in this account, 03 to 49, continuations included");
            }
            case "98" -> {
                add(out, r, 0, "Group control total", Bai2Format.money(r.field(0), cur) + " · sum of the group's account control totals");
                add(out, r, 1, "Number of accounts", "");
                add(out, r, 2, "Number of records", "Records in this group, 02 to 98, continuations included");
            }
            case "99" -> {
                add(out, r, 0, "File control total", Bai2Format.money(r.field(0), cur) + " · sum of the group control totals");
                add(out, r, 1, "Number of groups", "");
                add(out, r, 2, "Number of records", "Records in the file, 01 to 99, continuations included");
            }
            default -> {
                for (int i = 0; i < r.fields().size(); i++) {
                    add(out, r, i, "Field " + (i + 1), "");
                }
            }
        }
        return out;
    }

    private static int fundsExtras(List<Field> out, Bai2Record r, String fundsType, int at, String cur, String suffix) {
        int extras = Bai2Parser.extraCount(fundsType, r, at);
        List<String> values = new ArrayList<>();
        for (int k = 0; k < extras; k++) {
            values.add(r.field(at + k));
        }
        List<String> names = new Bai2Funds(fundsType, values).valueNames();
        for (int k = 0; k < extras; k++) {
            String name = k < names.size() ? names.get(k) : "Funds detail";
            String v = r.field(at + k);
            String meaning = name.contains("amount") ? Bai2Format.money(v, cur)
                    : name.equals("Value date") ? Bai2Format.readableDate(v)
                    : name.equals("Value time") && !v.isEmpty() ? Bai2Format.readableTime(v) : "";
            add(out, r, at + k, name + suffix, meaning);
        }
        return at + extras;
    }

    private static void add(List<Field> out, Bai2Record r, int index, String name, String meaning) {
        out.add(new Field(name, r.field(index), meaning));
    }

    /** One-line summary of a record, e.g. "Credit $2,500.00 · ACH Credit Received · ref 0000123456". */
    public static String describe(Bai2File file, Bai2Record r) {
        if (r == null) {
            return "";
        }
        String cur = file.currencyOf(r);
        return switch (r.code()) {
            case "01" -> "From " + r.field(0) + " to " + r.field(1) + " · created " + Bai2Format.readableDate(r.field(2))
                    + (r.field(3).isEmpty() ? "" : " " + Bai2Format.readableTime(r.field(3))) + " · file ID " + r.field(4);
            case "02" -> "From " + r.field(1) + " for " + r.field(0) + " · as of " + Bai2Format.readableDate(r.field(3))
                    + " · " + Bai2Codes.describe(Bai2Codes.GROUP_STATUS, r.field(2))
                    + (r.field(6).isEmpty() ? "" : " · " + Bai2Codes.describe(Bai2Codes.AS_OF_MODIFIER, r.field(6)));
            case "03" -> {
                List<Bai2Summary> s = Bai2Parser.summaries(r);
                yield "Account " + r.field(0) + " (" + cur + ") · " + s.size() + " balance/summary amount(s)";
            }
            case "16" -> {
                Bai2Transaction t = Bai2Parser.transaction(r);
                yield t.kind().label + " " + Bai2Format.money(t.amountRaw(), cur) + " · " + Bai2Codes.typeName(t.typeCode())
                        + (t.bankReference().isEmpty() ? "" : " · bank ref " + t.bankReference())
                        + (t.customerReference().isEmpty() ? "" : " · customer ref " + t.customerReference())
                        + (t.text().isEmpty() ? "" : " · " + t.text());
            }
            case "49" -> "Account control total " + Bai2Format.money(r.field(0), cur) + " · " + r.field(1) + " record(s)";
            case "98" -> "Group control total " + Bai2Format.money(r.field(0), cur) + " · " + r.field(1) + " account(s) · " + r.field(2) + " record(s)";
            case "99" -> "File control total " + Bai2Format.money(r.field(0), cur) + " · " + r.field(1) + " group(s) · " + r.field(2) + " record(s)";
            default -> Bai2Codes.RECORD_TYPE.getOrDefault(r.code(), "Unknown record");
        };
    }

    public static String recordTypeLabel(Bai2Record r) {
        return Bai2Codes.RECORD_TYPE.getOrDefault(r.code(), "Unknown record \"" + r.code() + "\"");
    }

    /**
     * Splits each physical line of a record into its fields, so the raw view can shade each field
     * and name it in a tooltip. Commas stay attached to the field before them.
     */
    public static List<List<Segment>> segments(Bai2File file, Bai2Record r) {
        List<Field> fields = fields(file, r);
        int textIndex = r.code().equals("16") ? fields.size() - 1 : -1;
        List<List<Segment>> out = new ArrayList<>();
        int next = 0;
        boolean inText = false;
        for (int k = 0; k < r.lines().size(); k++) {
            String line = r.lines().get(k).text();
            List<Segment> segs = new ArrayList<>();
            int comma = line.indexOf(',');
            String code = comma < 0 ? line : line.substring(0, comma + 1);
            segs.add(new Segment(code, -1, k == 0 ? "Record code: " + recordTypeLabel(r) : "Continuation (88) of the record above"));
            String rest = comma < 0 ? "" : line.substring(comma + 1);
            if (inText) {
                segs.add(new Segment(rest, textIndex, "Text (continued)"));
                out.add(segs);
                continue;
            }
            int pos = 0;
            while (pos < rest.length()) {
                if (next == textIndex) {
                    segs.add(new Segment(rest.substring(pos), next, fields.get(next).name()));
                    inText = true;
                    break;
                }
                int c = rest.indexOf(',', pos);
                int end = c < 0 ? rest.length() : c + 1;
                String name = next < fields.size() ? fields.get(next).name() : "Extra field";
                segs.add(new Segment(rest.substring(pos, end), next, name));
                next++;
                pos = end;
            }
            out.add(segs);
        }
        return out;
    }
}
