package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;

import java.util.List;

/**
 * Renders a document as a self-contained, printable HTML page.
 */
public final class AchReport {

    private AchReport() {
    }

    public static String html(AchService service, ACHDocument doc, String fileName) {
        AchSummary.FileInfo f = AchSummary.file(doc);
        List<AchValidator.Issue> issues = AchValidator.validate(doc);
        StringBuilder h = new StringBuilder();
        h.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>ACH report – ").append(esc(fileName))
                .append("</title><style>")
                .append("body{font:14px/1.45 system-ui,sans-serif;margin:32px;color:#1d2433}")
                .append("h1{font-size:22px;margin:0 0 4px}h2{font-size:17px;margin:28px 0 6px}")
                .append(".muted{color:#5f6b7a}.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:16px 0}")
                .append(".card{border:1px solid #d8dee6;border-radius:8px;padding:10px 12px}")
                .append(".card b{display:block;font-size:12px;color:#5f6b7a;font-weight:600;text-transform:uppercase}")
                .append("table{border-collapse:collapse;width:100%;margin-top:6px}th,td{padding:6px 8px;border-bottom:1px solid #e3e7ec;text-align:left}")
                .append("th{font-size:12px;color:#5f6b7a;text-transform:uppercase}td.num{text-align:right;font-variant-numeric:tabular-nums}")
                .append(".credit{color:#1a7f37}.debit{color:#b42318}.err{color:#b42318}.warn{color:#9a6700}")
                .append("tfoot td{font-weight:600}@media print{body{margin:12px}}")
                .append("</style></head><body>");

        h.append("<h1>ACH file ").append(esc(fileName)).append("</h1>")
                .append("<div class=muted>Created ").append(esc(f.created())).append(" · File ID ").append(esc(f.fileId())).append("</div>");
        h.append("<div class=grid>")
                .append(card("From", esc(f.originName()) + "<br><span class=muted>" + esc(f.origin()) + "</span>", true))
                .append(card("To", esc(f.destinationName()) + "<br><span class=muted>" + esc(f.destination()) + "</span>", true))
                .append(card("Credits", AchFormat.money(f.credits()), false))
                .append(card("Debits", AchFormat.money(f.debits()), false))
                .append(card("Batches", String.valueOf(f.batches()), false))
                .append(card("Entries", String.valueOf(f.entries()), false))
                .append(card("Net (credits − debits)", AchFormat.money(f.net()), false))
                .append(card("Validation", issues.isEmpty() ? "No problems found" : issues.size() + " issue(s)", false))
                .append("</div>");

        for (ACHBatch batch : doc.getBatches()) {
            AchSummary.BatchInfo b = AchSummary.batch(batch);
            h.append("<h2>").append(esc(b.title())).append("</h2>")
                    .append("<div class=muted>").append(esc(b.secMeaning())).append(" · ").append(esc(b.serviceClass()))
                    .append(" · Effective ").append(esc(b.effectiveDate())).append(" · Company ID ").append(esc(b.companyId()))
                    .append(" · Originating bank ").append(esc(b.odfi())).append("</div>");
            h.append("<table><thead><tr><th>#</th><th>Receiver</th><th>ID</th><th>Bank routing</th><th>Account</th>")
                    .append("<th>Type</th><th>Direction</th><th>Amount</th><th>Trace</th><th>Notes</th></tr></thead><tbody>");
            int i = 0;
            for (ACHBatchDetail d : batch.getDetails()) {
                AchSummary.EntryInfo e = AchSummary.entry(service, d);
                String cls = e.direction().startsWith("Credit") ? "credit" : e.direction().startsWith("Debit") ? "debit" : "";
                h.append("<tr><td>").append(++i).append("</td><td>").append(esc(e.name())).append("</td><td>").append(esc(e.idNumber()))
                        .append("</td><td>").append(esc(e.routing())).append("</td><td>").append(esc(e.account()))
                        .append("</td><td>").append(esc(e.accountType())).append("</td><td class=").append(cls).append(">")
                        .append(esc(e.direction())).append("</td><td class=num>").append(AchFormat.money(e.amount()))
                        .append("</td><td>").append(esc(e.trace())).append("</td><td>").append(esc(e.note())).append("</td></tr>");
            }
            h.append("</tbody><tfoot><tr><td colspan=7>").append(b.entries()).append(" entries · credits ")
                    .append(AchFormat.money(b.credits())).append(" · debits ").append(AchFormat.money(b.debits()))
                    .append("</td><td colspan=3></td></tr></tfoot></table>");
        }

        h.append("<h2>Validation</h2>");
        if (issues.isEmpty()) {
            h.append("<p>No problems found. Control totals, hashes and check digits all agree.</p>");
        } else {
            h.append("<ul>");
            for (AchValidator.Issue issue : issues) {
                h.append("<li class=").append(issue.severity() == AchValidator.Severity.ERROR ? "err" : "warn").append(">")
                        .append(esc(issue.toString())).append("</li>");
            }
            h.append("</ul>");
        }
        return h.append("</body></html>").toString();
    }

    private static String card(String label, String value, boolean valueIsHtml) {
        return "<div class=card><b>" + esc(label) + "</b>" + (valueIsHtml ? value : esc(value)) + "</div>";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
