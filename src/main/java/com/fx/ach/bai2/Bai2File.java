package com.fx.ach.bai2;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed BAI2 file. Parsing is lenient: a file with structural problems still yields a model,
 * and the problems are listed in {@link #parseIssues()}.
 */
public final class Bai2File {

    private final Bai2Record header;
    private final List<Bai2Group> groups;
    private final Bai2Record trailer;
    private final List<Bai2Record> records;
    private final List<Bai2Line> lines;
    private final List<Bai2Issue> parseIssues;
    private final Map<Bai2Line, Bai2Record> recordOfLine = new HashMap<>();
    private final Map<Bai2Record, String> currencyOf = new IdentityHashMap<>();

    Bai2File(Bai2Record header, List<Bai2Group> groups, Bai2Record trailer, List<Bai2Record> records,
             List<Bai2Line> lines, List<Bai2Issue> parseIssues) {
        this.header = header;
        this.groups = List.copyOf(groups);
        this.trailer = trailer;
        this.records = List.copyOf(records);
        this.lines = List.copyOf(lines);
        this.parseIssues = List.copyOf(parseIssues);
        for (Bai2Record r : records) {
            for (Bai2Line l : r.lines()) {
                recordOfLine.put(l, r);
            }
        }
        for (Bai2Group g : groups) {
            put(g.header(), g.currency());
            put(g.trailer(), g.currency());
            for (Bai2Account a : g.accounts()) {
                put(a.header(), a.currency());
                put(a.trailer(), a.currency());
                a.transactions().forEach(t -> put(t.record(), a.currency()));
            }
        }
    }

    private void put(Bai2Record r, String currency) {
        if (r != null) {
            currencyOf.put(r, currency);
        }
    }

    /** File header (01), or null if the file doesn't start with one. */
    public Bai2Record header() {
        return header;
    }

    public List<Bai2Group> groups() {
        return groups;
    }

    /** File trailer (99), or null if missing. */
    public Bai2Record trailer() {
        return trailer;
    }

    /** Every logical record in file order, including any that don't fit the structure. */
    public List<Bai2Record> records() {
        return records;
    }

    /** Every non-blank physical line in file order. */
    public List<Bai2Line> lines() {
        return lines;
    }

    public List<Bai2Issue> parseIssues() {
        return parseIssues;
    }

    public Bai2Record recordOf(Bai2Line line) {
        return recordOfLine.get(line);
    }

    /** The currency amounts in this record are in (its account's, else its group's, else USD). */
    public String currencyOf(Bai2Record record) {
        String c = currencyOf.get(record);
        if (c != null) {
            return c;
        }
        return groups.isEmpty() ? "USD" : groups.get(0).currency();
    }

    public List<Bai2Account> accounts() {
        return groups.stream().flatMap(g -> g.accounts().stream()).toList();
    }

    public String sender() {
        return field(0);
    }

    public String receiver() {
        return field(1);
    }

    public String creationDate() {
        return field(2);
    }

    public String creationTime() {
        return field(3);
    }

    public String fileId() {
        return field(4);
    }

    public String version() {
        return field(7);
    }

    /** What the file trailer's control total should be: the sum of its groups' totals. */
    public long computedControlTotal() {
        return groups.stream().mapToLong(Bai2Group::computedControlTotal).sum();
    }

    /** What the file trailer's record count should be: every physical record, 01 and 99 included. */
    public int computedRecordCount() {
        return lines.size();
    }

    private String field(int i) {
        return header == null ? "" : header.field(i);
    }
}
