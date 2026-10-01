package com.fx.ach.bai2;

import com.fx.ach.bai2.Bai2Issue.Severity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads BAI2 (BAI Cash Management Balance Reporting, version 2) text. The file extension is never
 * looked at: {@link #looksLikeBai2} decides from the content, so .txt, .bai, .dat or no extension
 * all work.
 *
 * <p>Lenient by design: CR/LF or LF line endings, trailing padding, blank lines, a UTF-8 byte-order
 * mark and files with no line breaks at all are accepted, and structural problems are reported as
 * issues instead of stopping the read.
 */
public final class Bai2Parser {

    /** Where a record starts in a file with no line breaks: after a "/" terminator, before "NN,". */
    private static final Pattern RECORD_START = Pattern.compile("(?<=/)\\s*(?=(?:01|02|03|16|49|88|98|99),)");

    private Bai2Parser() {
    }

    /** True when the first non-blank line is a BAI2 file header ("01,…"). */
    public static boolean looksLikeBai2(String text) {
        if (text == null) {
            return false;
        }
        for (String line : stripBom(text).split("\\R")) {
            if (!line.isBlank()) {
                return line.strip().startsWith("01,");
            }
        }
        return false;
    }

    public static Bai2File parse(String text) {
        List<Bai2Issue> issues = new ArrayList<>();
        List<Bai2Line> lines = lines(text);
        List<Bai2Record> records = records(lines, issues);
        return structure(records, lines, issues);
    }

    // ---- physical lines --------------------------------------------------------------------

    static List<Bai2Line> lines(String text) {
        String[] physical = stripBom(text).split("\\R", -1);
        List<Bai2Line> lines = new ArrayList<>();
        long nonBlank = Arrays.stream(physical).filter(l -> !l.isBlank()).count();
        if (nonBlank == 1) {
            // a whole file on one line: split it at the "/" that ends each record
            String only = Arrays.stream(physical).filter(l -> !l.isBlank()).findFirst().orElse("");
            int n = 0;
            for (String part : RECORD_START.split(only.strip())) {
                lines.add(new Bai2Line(++n, part.stripTrailing()));
            }
            return lines;
        }
        for (int i = 0; i < physical.length; i++) {
            if (!physical[i].isBlank()) {
                lines.add(new Bai2Line(i + 1, physical[i].stripTrailing()));
            }
        }
        return lines;
    }

    private static String stripBom(String text) {
        if (text.startsWith("﻿")) {
            return text.substring(1);
        }
        // a UTF-8 BOM read as ISO-8859-1
        return text.startsWith("ï»¿") ? text.substring(3) : text;
    }

    // ---- logical records -------------------------------------------------------------------

    private static List<Bai2Record> records(List<Bai2Line> lines, List<Bai2Issue> issues) {
        List<List<Bai2Line>> grouped = new ArrayList<>();
        for (Bai2Line line : lines) {
            if (line.code().equals("88") && !grouped.isEmpty()) {
                grouped.get(grouped.size() - 1).add(line);
            } else {
                grouped.add(new ArrayList<>(List.of(line)));
            }
        }
        List<Bai2Record> records = new ArrayList<>();
        for (List<Bai2Line> g : grouped) {
            Bai2Record r = record(g);
            if (r.code().equals("88")) {
                issues.add(new Bai2Issue(Severity.ERROR, "Continuation record (88) has no record before it to continue.", r));
            }
            records.add(r);
        }
        return records;
    }

    /**
     * Joins a record with its continuations into one field stream, then splits it into fields. A
     * continuation starts a new field after a "/" terminator, carries straight on when the line
     * ended with a comma, and continues the text when it follows a 16 record's text.
     */
    static Bai2Record record(List<Bai2Line> lines) {
        String code = lines.get(0).code();
        StringBuilder s = new StringBuilder(lines.get(0).body());
        List<Integer> joins = new ArrayList<>(); // positions of commas inserted between lines
        for (int k = 1; k < lines.size(); k++) {
            // "/" closes the last field (even an empty one after ",/"), so a separator always follows it
            boolean terminated = s.length() > 0 && s.charAt(s.length() - 1) == '/';
            if (terminated) {
                s.setLength(s.length() - 1);
            }
            if (terminated || s.length() == 0 || s.charAt(s.length() - 1) != ',') {
                joins.add(s.length());
                s.append(',');
            }
            s.append(lines.get(k).body());
        }
        if (code.equals("16")) {
            return transactionRecord(lines, s.toString(), joins);
        }
        String joined = s.toString();
        int slash = joined.indexOf('/');
        if (slash >= 0) {
            joined = joined.substring(0, slash);
        }
        List<String> fields = joined.isEmpty() ? List.of() : List.of(joined.split(",", -1));
        return new Bai2Record(code, List.copyOf(lines), fields, "");
    }

    /** 16 records end in free-form text that may itself contain commas and slashes. */
    private static Bai2Record transactionRecord(List<Bai2Line> lines, String s, List<Integer> joins) {
        Cursor c = new Cursor(s);
        List<String> fields = new ArrayList<>();
        fields.add(c.next()); // type code
        fields.add(c.next()); // amount
        String fundsType = c.next();
        fields.add(fundsType);
        int extras = switch (fundsType.strip()) {
            case "S" -> 3;
            case "V" -> 2;
            case "D" -> -1;
            default -> 0;
        };
        if (extras == -1) {
            String count = c.next();
            fields.add(count);
            extras = 2 * (count.strip().matches("\\d{1,3}") ? Integer.parseInt(count.strip()) : 0);
        }
        for (int i = 0; i < extras; i++) {
            fields.add(c.next());
        }
        fields.add(c.next()); // bank reference
        fields.add(c.next()); // customer reference
        int textStart = c.pos;
        StringBuilder text = new StringBuilder(c.rest());
        for (int j : joins) {
            if (j >= textStart && j - textStart < text.length() && text.charAt(j - textStart) == ',') {
                text.setCharAt(j - textStart, ' '); // text continued on an 88 line
            }
        }
        String t = text.toString().strip();
        if (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1).strip();
        }
        return new Bai2Record("16", List.copyOf(lines), List.copyOf(fields), t);
    }

    /** Reads comma-separated fields; a "/" ends the record, so later fields read as blank. */
    private static final class Cursor {
        private final String s;
        private int pos;

        Cursor(String s) {
            this.s = s;
        }

        String next() {
            if (pos > s.length()) {
                return "";
            }
            int comma = s.indexOf(',', pos);
            int slash = s.indexOf('/', pos);
            int end = comma < 0 ? s.length() : comma;
            if (slash >= 0 && slash < end) {
                String field = s.substring(pos, slash);
                pos = s.length() + 1; // terminated: everything after is defaulted
                return field;
            }
            String field = s.substring(pos, end);
            pos = end + 1;
            return field;
        }

        String rest() {
            return pos > s.length() ? "" : s.substring(pos);
        }
    }

    // ---- file → groups → accounts ----------------------------------------------------------

    private static Bai2File structure(List<Bai2Record> records, List<Bai2Line> lines, List<Bai2Issue> issues) {
        Bai2Record fileHeader = null;
        Bai2Record fileTrailer = null;
        List<Bai2Group> groups = new ArrayList<>();
        GroupBuilder group = null;
        AccountBuilder account = null;

        for (int i = 0; i < records.size(); i++) {
            Bai2Record r = records.get(i);
            if (fileTrailer != null) {
                issues.add(new Bai2Issue(Severity.ERROR, "Record found after the file trailer (99).", r));
                continue;
            }
            switch (r.code()) {
                case "01" -> {
                    if (i > 0 || fileHeader != null) {
                        issues.add(new Bai2Issue(Severity.ERROR, "File header (01) must be the first record and appear only once.", r));
                    } else {
                        fileHeader = r;
                    }
                }
                case "02" -> {
                    account = closeAccount(account, group, issues);
                    group = closeGroup(group, groups, issues);
                    group = new GroupBuilder(r);
                }
                case "03" -> {
                    account = closeAccount(account, group, issues);
                    if (group == null) {
                        issues.add(new Bai2Issue(Severity.ERROR, "Account identifier (03) is not inside a group: the group header (02) is missing.", r));
                        group = new GroupBuilder(null);
                    }
                    account = new AccountBuilder(r, group.currency());
                }
                case "16" -> {
                    if (account == null) {
                        issues.add(new Bai2Issue(Severity.ERROR, "Transaction detail (16) is not inside an account: the account identifier (03) is missing.", r));
                    } else {
                        account.transactions.add(transaction(r));
                    }
                }
                case "49" -> {
                    if (account == null) {
                        issues.add(new Bai2Issue(Severity.ERROR, "Account trailer (49) has no account identifier (03) before it.", r));
                    } else {
                        account.trailer = r;
                        account = closeAccount(account, group, issues);
                    }
                }
                case "98" -> {
                    account = closeAccount(account, group, issues);
                    if (group == null) {
                        issues.add(new Bai2Issue(Severity.ERROR, "Group trailer (98) has no group header (02) before it.", r));
                    } else {
                        group.trailer = r;
                        group = closeGroup(group, groups, issues);
                    }
                }
                case "99" -> {
                    account = closeAccount(account, group, issues);
                    group = closeGroup(group, groups, issues);
                    fileTrailer = r;
                }
                case "88" -> {
                    // orphan continuation, already reported
                }
                default -> issues.add(new Bai2Issue(Severity.ERROR, "Unknown record code \"" + r.code()
                        + "\". BAI2 records start with 01, 02, 03, 16, 88, 49, 98 or 99.", r));
            }
        }
        closeAccount(account, group, issues);
        closeGroup(group, groups, issues);
        if (fileHeader == null) {
            issues.add(0, new Bai2Issue(Severity.ERROR, "File header (01) is missing.", null));
        }
        if (fileTrailer == null) {
            issues.add(new Bai2Issue(Severity.ERROR, "File trailer (99) is missing; the file may be cut short.",
                    records.isEmpty() ? null : records.get(records.size() - 1)));
        }
        return new Bai2File(fileHeader, groups, fileTrailer, records, lines, issues);
    }

    private static AccountBuilder closeAccount(AccountBuilder account, GroupBuilder group, List<Bai2Issue> issues) {
        if (account != null) {
            if (account.trailer == null) {
                issues.add(new Bai2Issue(Severity.ERROR, "Account " + account.header.field(0) + " has no account trailer (49).", account.header));
            }
            group.accounts.add(account.build());
        }
        return null;
    }

    private static GroupBuilder closeGroup(GroupBuilder group, List<Bai2Group> groups, List<Bai2Issue> issues) {
        if (group != null) {
            if (group.trailer == null) {
                issues.add(new Bai2Issue(Severity.ERROR, "Group has no group trailer (98).",
                        group.header != null ? group.header : group.accounts.isEmpty() ? null : group.accounts.get(0).header()));
            }
            groups.add(new Bai2Group(group.header, List.copyOf(group.accounts), group.trailer));
        }
        return null;
    }

    private static final class GroupBuilder {
        final Bai2Record header;
        final List<Bai2Account> accounts = new ArrayList<>();
        Bai2Record trailer;

        GroupBuilder(Bai2Record header) {
            this.header = header;
        }

        String currency() {
            return new Bai2Group(header, List.of(), null).currency();
        }
    }

    private static final class AccountBuilder {
        final Bai2Record header;
        final String currency;
        final List<Bai2Transaction> transactions = new ArrayList<>();
        Bai2Record trailer;

        AccountBuilder(Bai2Record header, String groupCurrency) {
            this.header = header;
            String own = header.field(1);
            this.currency = own.isEmpty() ? groupCurrency : own;
        }

        Bai2Account build() {
            return new Bai2Account(header, header.field(0), currency, summaries(header), List.copyOf(transactions), trailer);
        }
    }

    // ---- field groups ----------------------------------------------------------------------

    /** The type-code groups on a 03 record, after the account number and currency. */
    static List<Bai2Summary> summaries(Bai2Record r) {
        List<Bai2Summary> list = new ArrayList<>();
        int i = 2;
        while (i < r.fields().size()) {
            String type = r.field(i);
            String amount = r.field(i + 1);
            String count = r.field(i + 2);
            String fundsType = r.field(i + 3);
            i += 4;
            int extras = extraCount(fundsType, r, i);
            List<String> values = new ArrayList<>();
            for (int k = 0; k < extras; k++) {
                values.add(r.field(i + k));
            }
            i += extras;
            if (type.isEmpty() && amount.isEmpty() && count.isEmpty() && fundsType.isEmpty()) {
                continue; // trailing empty group
            }
            list.add(new Bai2Summary(type, amount, count, new Bai2Funds(fundsType, values)));
        }
        return list;
    }

    static Bai2Transaction transaction(Bai2Record r) {
        String fundsType = r.field(2);
        int extras = extraCount(fundsType, r, 3);
        List<String> values = new ArrayList<>();
        for (int k = 0; k < extras; k++) {
            values.add(r.field(3 + k));
        }
        return new Bai2Transaction(r, r.field(0), r.field(1), new Bai2Funds(fundsType, values),
                r.field(3 + extras), r.field(4 + extras), r.text());
    }

    /** How many fields follow a funds type code: S → 3, V → 2, D → 1 + 2 × count, others → 0. */
    static int extraCount(String fundsType, Bai2Record r, int at) {
        return switch (fundsType) {
            case "S" -> 3;
            case "V" -> 2;
            case "D" -> {
                String n = r.field(at);
                yield 1 + 2 * (n.matches("\\d{1,3}") ? Integer.parseInt(n) : 0);
            }
            default -> 0;
        };
    }
}
