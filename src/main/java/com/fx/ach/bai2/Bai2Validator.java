package com.fx.ach.bai2;

import com.fx.ach.bai2.Bai2Issue.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks a parsed BAI2 file: structure problems found while reading, field formats, and the
 * control totals and record counts in the account (49), group (98) and file (99) trailers.
 */
public final class Bai2Validator {

    private Bai2Validator() {
    }

    public static List<Bai2Issue> validate(Bai2File file) {
        List<Bai2Issue> issues = new ArrayList<>(file.parseIssues());
        String fileCurrency = file.currencyOf(file.header());

        Bai2Record h = file.header();
        if (h != null) {
            required(issues, h, 0, "Sender identification");
            required(issues, h, 1, "Receiver identification");
            date(issues, h, h.field(2), "File creation date", true);
            time(issues, h, h.field(3), "File creation time", true);
            required(issues, h, 4, "File identification number");
            if (!h.field(7).equals("2")) {
                issues.add(new Bai2Issue(Severity.WARNING, "Version number is " + (h.field(7).isEmpty() ? "missing" : "\"" + h.field(7) + "\"")
                        + "; this reader expects BAI version 2.", h));
            }
        }

        for (Bai2Group g : file.groups()) {
            Bai2Record gh = g.header();
            if (gh != null) {
                if (!Bai2Codes.GROUP_STATUS.containsKey(gh.field(2))) {
                    issues.add(new Bai2Issue(Severity.ERROR, "Group status \"" + gh.field(2) + "\" is not 1 (update), 2 (deletion), 3 (correction) or 4 (test only).", gh));
                }
                date(issues, gh, gh.field(3), "As-of date", true);
                time(issues, gh, gh.field(4), "As-of time", false);
                currency(issues, gh, gh.field(5));
                if (!gh.field(6).isEmpty() && !Bai2Codes.AS_OF_MODIFIER.containsKey(gh.field(6))) {
                    issues.add(new Bai2Issue(Severity.ERROR, "As-of date modifier \"" + gh.field(6) + "\" is not 1–4.", gh));
                }
            }
            for (Bai2Account a : g.accounts()) {
                validateAccount(issues, a);
            }
            Bai2Record gt = g.trailer();
            if (gt != null) {
                total(issues, gt, gt.field(0), g.computedControlTotal(), "Group control total", "its accounts' totals add up to", g.currency());
                count(issues, gt, gt.field(1), g.accounts().size(), "Number of accounts", "the group has");
                count(issues, gt, gt.field(2), g.computedRecordCount(), "Number of records", "the group has (02 to 98, continuations included)");
            }
        }

        Bai2Record t = file.trailer();
        if (t != null) {
            total(issues, t, t.field(0), file.computedControlTotal(), "File control total", "its groups' totals add up to", fileCurrency);
            count(issues, t, t.field(1), file.groups().size(), "Number of groups", "the file has");
            count(issues, t, t.field(2), file.computedRecordCount(), "Number of records", "the file has (01 to 99, continuations included)");
        }
        if (file.groups().isEmpty() && h != null) {
            issues.add(new Bai2Issue(Severity.WARNING, "File contains no groups.", h));
        }
        return issues;
    }

    private static void validateAccount(List<Bai2Issue> issues, Bai2Account a) {
        Bai2Record ah = a.header();
        required(issues, ah, 0, "Customer account number");
        currency(issues, ah, ah.field(1));
        for (Bai2Summary s : a.summaries()) {
            String type = "Type code " + (s.typeCode().isEmpty() ? "(blank)" : s.typeCode());
            if (!s.typeCode().matches("\\d{3}")) {
                issues.add(new Bai2Issue(Severity.ERROR, type + " on the account record is not a 3-digit code.", ah));
            }
            if (!s.amountRaw().isEmpty() && !Bai2Format.isAmount(s.amountRaw())) {
                issues.add(new Bai2Issue(Severity.ERROR, type + ": amount \"" + s.amountRaw() + "\" is not a number.", ah));
            }
            if (!s.itemCount().isEmpty() && !s.itemCount().matches("\\d+")) {
                issues.add(new Bai2Issue(Severity.ERROR, type + ": item count \"" + s.itemCount() + "\" is not a number.", ah));
            }
            funds(issues, ah, s.funds(), type);
        }
        for (Bai2Transaction t : a.transactions()) {
            Bai2Record r = t.record();
            if (!t.typeCode().matches("\\d{3}")) {
                issues.add(new Bai2Issue(Severity.ERROR, "Type code \"" + t.typeCode() + "\" is not a 3-digit code.", r));
            } else if (t.kind() == Bai2Codes.Kind.BALANCE) {
                issues.add(new Bai2Issue(Severity.WARNING, "Type code " + t.typeCode() + " is a balance code; transaction detail (16) "
                        + "records should use an activity code (100–799 or 900–999).", r));
            }
            if (t.amountRaw().isEmpty()) {
                issues.add(new Bai2Issue(Severity.WARNING, "Transaction has no amount.", r));
            } else if (!Bai2Format.isAmount(t.amountRaw())) {
                issues.add(new Bai2Issue(Severity.ERROR, "Amount \"" + t.amountRaw() + "\" is not a number.", r));
            } else if (t.amountRaw().startsWith("-") || t.amountRaw().startsWith("+")) {
                issues.add(new Bai2Issue(Severity.WARNING, "Transaction amounts should be unsigned; the type code says whether it is a credit or a debit.", r));
            }
            funds(issues, r, t.funds(), "Transaction");
        }
        Bai2Record at = a.trailer();
        if (at != null) {
            total(issues, at, at.field(0), a.computedControlTotal(), "Account control total for " + a.accountNumber(),
                    "the account's balances and transactions add up to", a.currency());
            count(issues, at, at.field(1), a.computedRecordCount(), "Number of records for account " + a.accountNumber(),
                    "the account has (03 to 49, continuations included)");
        }
    }

    private static void funds(List<Bai2Issue> issues, Bai2Record r, Bai2Funds f, String what) {
        if (!Bai2Codes.FUNDS_TYPE.containsKey(f.type())) {
            issues.add(new Bai2Issue(Severity.ERROR, what + ": funds type \"" + f.type() + "\" is not Z, 0, 1, 2, S, V or D.", r));
            return;
        }
        switch (f.type()) {
            case "S" -> {
                for (String v : f.values()) {
                    if (!v.isBlank() && !Bai2Format.isAmount(v)) {
                        issues.add(new Bai2Issue(Severity.ERROR, what + ": availability amount \"" + v.strip() + "\" is not a number.", r));
                    }
                }
            }
            case "V" -> {
                date(issues, r, f.value(0), what + ": value date", true);
                time(issues, r, f.value(1), what + ": value time", false);
            }
            case "D" -> {
                if (!f.value(0).matches("\\d+")) {
                    issues.add(new Bai2Issue(Severity.ERROR, what + ": number of distributions \"" + f.value(0) + "\" is not a number.", r));
                }
                for (int i = 1; i + 1 < f.values().size(); i += 2) {
                    if (!f.value(i).matches("\\d+") || !Bai2Format.isAmount(f.value(i + 1))) {
                        issues.add(new Bai2Issue(Severity.ERROR, what + ": distribution \"" + f.value(i) + "," + f.value(i + 1)
                                + "\" should be a number of days and an amount.", r));
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void required(List<Bai2Issue> issues, Bai2Record r, int field, String name) {
        if (r.field(field).isEmpty()) {
            issues.add(new Bai2Issue(Severity.ERROR, name + " is missing.", r));
        }
    }

    private static void date(List<Bai2Issue> issues, Bai2Record r, String value, String name, boolean required) {
        if (value.isEmpty()) {
            if (required) {
                issues.add(new Bai2Issue(Severity.ERROR, name + " is missing.", r));
            }
        } else if (Bai2Format.parseDate(value) == null) {
            issues.add(new Bai2Issue(Severity.ERROR, name + " \"" + value + "\" is not a valid YYMMDD date.", r));
        }
    }

    private static void time(List<Bai2Issue> issues, Bai2Record r, String value, String name, boolean required) {
        if (value.isEmpty()) {
            if (required) {
                issues.add(new Bai2Issue(Severity.WARNING, name + " is missing.", r));
            }
        } else if (!Bai2Format.isTime(value)) {
            issues.add(new Bai2Issue(Severity.ERROR, name + " \"" + value + "\" is not a valid HHMM time.", r));
        }
    }

    private static void currency(List<Bai2Issue> issues, Bai2Record r, String value) {
        if (!value.isEmpty() && !value.matches("[A-Z]{3}")) {
            issues.add(new Bai2Issue(Severity.WARNING, "Currency code \"" + value + "\" is not a 3-letter ISO code.", r));
        }
    }

    private static void total(List<Bai2Issue> issues, Bai2Record r, String raw, long expected, String name, String explanation, String currency) {
        Long declared = Bai2Format.parseAmount(raw);
        if (declared == null) {
            issues.add(new Bai2Issue(Severity.ERROR, name + " \"" + raw + "\" is not a number.", r));
        } else if (declared != expected) {
            issues.add(new Bai2Issue(Severity.ERROR, name + " is " + Bai2Format.money(declared, currency) + " (" + raw + ") but "
                    + explanation + " " + Bai2Format.money(expected, currency) + " (" + expected + ").", r));
        }
    }

    private static void count(List<Bai2Issue> issues, Bai2Record r, String raw, int expected, String name, String explanation) {
        if (!raw.matches("\\d{1,9}")) {
            issues.add(new Bai2Issue(Severity.ERROR, name + " \"" + raw + "\" is not a number.", r));
        } else if (Long.parseLong(raw) != expected) {
            issues.add(new Bai2Issue(Severity.ERROR, name + " is " + raw + " but " + explanation + " " + expected + ".", r));
        }
    }
}
