package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.AddendaRecord;
import com.afrunt.jach.domain.BatchHeader;
import com.afrunt.jach.domain.EntryDetail;
import com.afrunt.jach.domain.FileHeader;
import com.afrunt.jach.domain.GeneralBatchHeader;
import com.afrunt.jach.domain.addenda.BaseCORAddendaRecord;
import com.afrunt.jach.domain.addenda.CORAddendaRecord;
import com.afrunt.jach.domain.addenda.GeneralAddendaRecord;
import com.afrunt.jach.domain.addenda.ReturnAddendaRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Person-friendly views of a document: who sent what to whom, for how much.
 */
public final class AchSummary {

    private AchSummary() {
    }

    public record FileInfo(String originName, String origin, String destinationName, String destination,
                           String created, String fileId, int batches, int entries,
                           BigDecimal credits, BigDecimal debits) {
        public BigDecimal net() {
            return credits.subtract(debits);
        }
    }

    public record BatchInfo(int number, String companyName, String companyId, String sec, String secMeaning,
                            String description, String effectiveDate, String serviceClass, String odfi,
                            int entries, BigDecimal credits, BigDecimal debits) {
        public String title() {
            return "Batch " + number + " · " + companyName + " · " + sec + " " + description;
        }
    }

    public record EntryInfo(String name, String idNumber, String routing, String account, String accountType,
                            String direction, BigDecimal amount, String trace, String note, int transactionCode) {
    }

    public static FileInfo file(ACHDocument doc) {
        FileHeader fh = doc.getFileHeader();
        AchControls.Totals t = AchControls.totals(doc);
        String created = AchFormat.readable(fh.getFileCreationDate());
        String time = AchFormat.hhmm(fh.getFileCreationTime());
        return new FileInfo(AchFormat.trim(fh.getImmediateOriginName()), AchFormat.trim(fh.getImmediateOrigin()),
                AchFormat.trim(fh.getImmediateDestinationName()), AchFormat.trim(fh.getImmediateDestination()),
                time.isEmpty() ? created : created + " at " + time, AchFormat.trim(fh.getFileIdModifier()),
                doc.getBatches().size(), t.entries(), t.credits(), t.debits());
    }

    public static BatchInfo batch(ACHBatch batch) {
        BatchHeader bh = batch.getBatchHeader();
        String name = bh instanceof GeneralBatchHeader g ? AchFormat.trim(g.getCompanyName()) : "International";
        String sec = AchFormat.trim(bh.getStandardEntryClassCode());
        AchControls.Totals t = AchControls.totals(batch);
        return new BatchInfo(bh.getBatchNumber() == null ? 0 : bh.getBatchNumber(), name,
                AchFormat.trim(AchControls.companyId(bh)), sec, AchCodes.describe(AchCodes.SEC, sec),
                AchFormat.trim(bh.getCompanyEntryDescription()), AchFormat.readable(bh.getEffectiveEntryDate()),
                AchCodes.describe(AchCodes.SERVICE_CLASS, bh.getServiceClassCode()),
                AchFormat.trim(bh.getOriginatorDFIIdentifier()), t.entries(), t.credits(), t.debits());
    }

    public static EntryInfo entry(AchService service, ACHBatchDetail detail) {
        EntryDetail e = detail.getDetailRecord();
        String name = "";
        String id = "";
        String account = "";
        for (FieldView f : service.fields(e)) {
            String n = f.name();
            if (name.isEmpty() && n.contains("Name")) {
                name = f.value();
            } else if (id.isEmpty() && n.contains("Identification Number") && !n.contains("DFI")) {
                id = f.value();
            } else if (n.equals("DFI Account Number") || n.equals("Foreign Receiver's Account Number")) {
                account = f.value();
            }
        }
        int code = e.getTransactionCode() == null ? 0 : e.getTransactionCode();
        String direction = AchCodes.isPrenote(code) ? "Prenote" : AchCodes.isCredit(code) ? "Credit" : AchCodes.isDebit(code) ? "Debit" : "?";
        if (code % 10 == 1 || code % 10 == 6) {
            direction = "Return/NOC " + (AchCodes.isCredit(code) ? "credit" : "debit");
        }
        String routing = AchFormat.trim(e.getReceivingDfiIdentification()) + (e.getCheckDigit() == null ? "" : e.getCheckDigit());
        String trace = e.getTraceNumber() == null ? "" : String.format("%015d", e.getTraceNumber());
        return new EntryInfo(name, id, routing, account, AchCodes.accountType(code), direction,
                e.getAmount() == null ? BigDecimal.ZERO : e.getAmount(), trace, note(detail), code);
    }

    /** One-line summary of an entry's addenda: remittance text, return reason or NOC. */
    public static String note(ACHBatchDetail detail) {
        List<String> parts = new ArrayList<>();
        for (AddendaRecord a : detail.getAddendaRecords()) {
            parts.add(addenda(a));
        }
        return String.join(" | ", parts);
    }

    public static String addenda(AddendaRecord a) {
        if (a instanceof GeneralAddendaRecord g) {
            return AchFormat.trim(g.getPaymentRelatedInformation());
        }
        if (a instanceof ReturnAddendaRecord r) {
            String code = AchFormat.trim(r.getReturnReasonCode());
            return "Return " + code + " – " + AchCodes.describe(AchCodes.RETURN_REASON, code);
        }
        if (a instanceof CORAddendaRecord c) {
            String code = AchFormat.trim(c.getChangeCode());
            return "NOC " + code + " – " + AchCodes.describe(AchCodes.CHANGE_CODE, code)
                    + " → " + AchFormat.trim(c.getCorrectedData());
        }
        if (a instanceof BaseCORAddendaRecord c) {
            return "NOC → " + AchFormat.trim(c.getCorrectedData());
        }
        return AchCodes.describe(AchCodes.ADDENDA_TYPE, a.getAddendaTypeCode());
    }
}
