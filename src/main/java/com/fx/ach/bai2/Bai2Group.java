package com.fx.ach.bai2;

import java.util.List;

/**
 * A group: its header (02), accounts and trailer (98).
 *
 * @param header  null when accounts appear without a 02 record
 * @param trailer null when the file is missing the 98 record
 */
public record Bai2Group(Bai2Record header, List<Bai2Account> accounts, Bai2Record trailer) {

    public String ultimateReceiver() {
        return field(0);
    }

    public String originator() {
        return field(1);
    }

    public String status() {
        return field(2);
    }

    public String asOfDate() {
        return field(3);
    }

    public String asOfTime() {
        return field(4);
    }

    /** The group's currency; BAI2 defaults a blank one to the originator's, assumed to be USD. */
    public String currency() {
        String c = field(5);
        return c.isEmpty() ? "USD" : c;
    }

    public String asOfModifier() {
        return field(6);
    }

    /** What the group trailer's control total should be: the sum of its accounts' totals. */
    public long computedControlTotal() {
        return accounts.stream().mapToLong(Bai2Account::computedControlTotal).sum();
    }

    /** What the trailer's record count should be: every line present from the 02 to the 98 inclusive. */
    public int computedRecordCount() {
        int count = (header == null ? 0 : header.lines().size()) + (trailer == null ? 0 : trailer.lines().size());
        for (Bai2Account a : accounts) {
            count += a.computedRecordCount();
        }
        return count;
    }

    private String field(int i) {
        return header == null ? "" : header.field(i);
    }
}
