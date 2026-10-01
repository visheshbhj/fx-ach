package com.fx.ach.bai2;

import java.util.List;

/**
 * An account: its identifier (03) record, the transactions (16) under it and its trailer (49).
 *
 * @param currency the account's currency, falling back to the group's when the 03 record leaves it blank
 * @param trailer  null when the file is missing the 49 record
 */
public record Bai2Account(Bai2Record header, String accountNumber, String currency, List<Bai2Summary> summaries,
                          List<Bai2Transaction> transactions, Bai2Record trailer) {

    /** What the account trailer's control total should be: every 03 amount plus every 16 amount. */
    public long computedControlTotal() {
        long total = 0;
        for (Bai2Summary s : summaries) {
            Long a = s.amount();
            total += a == null ? 0 : a;
        }
        for (Bai2Transaction t : transactions) {
            Long a = t.amount();
            total += a == null ? 0 : a;
        }
        return total;
    }

    /** What the trailer's record count should be: the 03, 16, 88 and 49 lines of this account that are present. */
    public int computedRecordCount() {
        int count = header.lines().size() + (trailer == null ? 0 : trailer.lines().size());
        for (Bai2Transaction t : transactions) {
            count += t.record().lines().size();
        }
        return count;
    }

    public long credits() {
        return sum(Bai2Codes.Kind.CREDIT);
    }

    public long debits() {
        return sum(Bai2Codes.Kind.DEBIT);
    }

    private long sum(Bai2Codes.Kind kind) {
        long total = 0;
        for (Bai2Transaction t : transactions) {
            Long a = t.amount();
            if (a != null && t.kind() == kind) {
                total += a;
            }
        }
        return total;
    }
}
