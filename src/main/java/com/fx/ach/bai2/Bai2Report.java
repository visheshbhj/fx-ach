package com.fx.ach.bai2;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Renders a BAI2 file as a self-contained, printable HTML page. */
public final class Bai2Report {

    private Bai2Report() {
    }

    public static String html(Bai2File file, String fileName) {
        List<Bai2Issue> issues = Bai2Validator.validate(file);
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>BAI2 report – ").append(esc(fileName))
                .append("</title><style>")
                .append("body{font:14px/1.45 system-ui,sans-serif;margin:32px;color:#1d2433}")
                .append("h1{font-size:22px;margin:0 0 4px}h2{font-size:18px;margin:30px 0 4px}h3{font-size:15px;margin:22px 0 4px}")
                .append(".muted{color:#5f6b7a}.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:16px 0}")
                .append(".card{border:1px solid #d8dee6;border-radius:8px;padding:10px 12px}")
                .append(".card b{display:block;font-size:12px;color:#5f6b7a;font-weight:600;text-transform:uppercase}")
                .append("table{border-collapse:collapse;width:100%;margin-top:6px}th,td{padding:6px 8px;border-bottom:1px solid #e3e7ec;text-align:left;vertical-align:top}")
                .append("th{font-size:12px;color:#5f6b7a;text-transform:uppercase}td.num{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}")
                .append(".credit{color:#1a7f37}.debit{color:#b42318}.err{color:#b42318}.warn{color:#9a6700}")
                .append("tfoot td{font-weight:600}@media print{body{margin:12px}}")
                .append("</style></head><body>");

        h.append("<h1>BAI2 file ").append(esc(fileName)).append("</h1>")
                .append("<div class=muted>Created ").append(esc(Bai2Format.readableDate(file.creationDate())))
                .append(file.creationTime().isEmpty() ? "" : " " + esc(Bai2Format.readableTime(file.creationTime())))
                .append(" · File ID ").append(esc(file.fileId())).append("</div>");
        List<Bai2Account> accounts = file.accounts();
        h.append("<div class=grid>")
                .append(card("From", file.sender()))
                .append(card("To", file.receiver()))
                .append(card("Groups / accounts", file.groups().size() + " / " + accounts.size()))
                .append(card("Transactions", String.valueOf(accounts.stream().mapToInt(a -> a.transactions().size()).sum())))
                .append(card("Credits", totals(accounts, true)))
                .append(card("Debits", totals(accounts, false)))
                .append(card("Validation", issues.isEmpty() ? "No problems found" : issues.size() + " issue(s)"))
                .append("</div>");

        int gn = 0;
        for (Bai2Group g : file.groups()) {
            h.append("<h2>Group ").append(++gn).append(" · ").append(esc(g.originator())).append(" → ").append(esc(g.ultimateReceiver())).append("</h2>")
                    .append("<div class=muted>As of ").append(esc(Bai2Format.readableDate(g.asOfDate())))
                    .append(g.asOfTime().isEmpty() ? "" : " " + esc(Bai2Format.readableTime(g.asOfTime())))
                    .append(" · ").append(esc(Bai2Codes.describe(Bai2Codes.GROUP_STATUS, g.status())))
                    .append(g.asOfModifier().isEmpty() ? "" : " · " + esc(Bai2Codes.describe(Bai2Codes.AS_OF_MODIFIER, g.asOfModifier())))
                    .append(" · ").append(esc(g.currency())).append("</div>");
            for (Bai2Account a : g.accounts()) {
                String cur = a.currency();
                h.append("<h3>Account ").append(esc(a.accountNumber())).append(" <span class=muted>(").append(esc(cur)).append(")</span></h3>");
                if (!a.summaries().isEmpty()) {
                    h.append("<table><thead><tr><th>Code</th><th>Balance / summary</th><th>Amount</th><th>Items</th><th>Availability</th></tr></thead><tbody>");
                    for (Bai2Summary s : a.summaries()) {
                        h.append("<tr><td>").append(esc(s.typeCode())).append("</td><td>").append(esc(Bai2Codes.typeName(s.typeCode())))
                                .append("</td><td class=num>").append(esc(Bai2Format.money(s.amountRaw(), cur)))
                                .append("</td><td>").append(esc(s.itemCount())).append("</td><td>")
                                .append(s.funds().type().isEmpty() ? "" : esc(s.funds().describe(cur))).append("</td></tr>");
                    }
                    h.append("</tbody></table>");
                }
                if (!a.transactions().isEmpty()) {
                    h.append("<table><thead><tr><th>#</th><th>Code</th><th>Transaction</th><th>Direction</th><th>Amount</th>")
                            .append("<th>Bank ref</th><th>Customer ref</th><th>Text</th></tr></thead><tbody>");
                    int i = 0;
                    for (Bai2Transaction t : a.transactions()) {
                        String cls = t.kind() == Bai2Codes.Kind.CREDIT ? "credit" : t.kind() == Bai2Codes.Kind.DEBIT ? "debit" : "";
                        h.append("<tr><td>").append(++i).append("</td><td>").append(esc(t.typeCode())).append("</td><td>")
                                .append(esc(Bai2Codes.typeName(t.typeCode()))).append("</td><td class=").append(cls).append(">")
                                .append(esc(t.kind().label)).append("</td><td class=num>").append(esc(Bai2Format.money(t.amountRaw(), cur)))
                                .append("</td><td>").append(esc(t.bankReference())).append("</td><td>").append(esc(t.customerReference()))
                                .append("</td><td>").append(esc(t.text())).append("</td></tr>");
                    }
                    h.append("</tbody><tfoot><tr><td colspan=8>").append(a.transactions().size()).append(" transaction(s) · credits ")
                            .append(esc(Bai2Format.money(a.credits(), cur))).append(" · debits ").append(esc(Bai2Format.money(a.debits(), cur)))
                            .append("</td></tr></tfoot></table>");
                } else {
                    h.append("<p class=muted>No transaction detail.</p>");
                }
            }
        }

        h.append("<h2>Validation</h2>");
        if (issues.isEmpty()) {
            h.append("<p>No problems found. Control totals and record counts all agree.</p>");
        } else {
            h.append("<ul>");
            for (Bai2Issue issue : issues) {
                h.append("<li class=").append(issue.severity() == Bai2Issue.Severity.ERROR ? "err" : "warn").append(">")
                        .append(esc(issue.toString())).append("</li>");
            }
            h.append("</ul>");
        }
        return h.append("</body></html>").toString();
    }

    /** Credit or debit totals across accounts, one figure per currency that has any ("$1.00 + €2.00"). */
    public static String totals(List<Bai2Account> accounts, boolean credits) {
        Map<String, Long> byCurrency = new TreeMap<>();
        for (Bai2Account a : accounts) {
            long amount = credits ? a.credits() : a.debits();
            if (amount != 0) {
                byCurrency.merge(a.currency(), amount, Long::sum);
            }
        }
        if (byCurrency.isEmpty()) {
            return Bai2Format.money(0, accounts.isEmpty() ? "USD" : accounts.get(0).currency());
        }
        StringBuilder sb = new StringBuilder();
        byCurrency.forEach((cur, v) -> sb.append(sb.length() > 0 ? " + " : "").append(Bai2Format.money(v, cur)));
        return sb.toString();
    }

    private static String card(String label, String value) {
        return "<div class=card><b>" + esc(label) + "</b>" + esc(value) + "</div>";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
