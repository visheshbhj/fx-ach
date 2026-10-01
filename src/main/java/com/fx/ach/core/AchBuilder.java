package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.EntryDetail;
import com.afrunt.jach.domain.FileControl;
import com.afrunt.jach.domain.FileHeader;
import com.afrunt.jach.domain.GeneralBatchHeader;
import com.afrunt.jach.domain.NonIATEntryDetail;
import com.afrunt.jach.domain.addenda.GeneralAddendaRecord;
import com.afrunt.jach.domain.detail.CCDEntryDetail;
import com.afrunt.jach.domain.detail.PPDEntryDetail;
import com.afrunt.jach.domain.detail.TELEntryDetail;
import com.afrunt.jach.domain.detail.WEBEntryDetail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Creates new files, batches and entries from plain inputs.
 */
public final class AchBuilder {

    /** SEC codes for which entries can be created from the UI. */
    public static final List<String> BUILDABLE_SEC = List.of("PPD", "CCD", "WEB", "TEL");

    private AchBuilder() {
    }

    public enum AccountType {
        CHECKING(20, "Checking"), SAVINGS(30, "Savings"), GENERAL_LEDGER(40, "General ledger"), LOAN(50, "Loan");

        private final int base;
        private final String label;

        AccountType(int base, String label) {
            this.base = base;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record FileSettings(String destinationRouting, String destinationName,
                               String origin, String originName, String fileIdModifier) {
    }

    public record BatchSettings(String sec, String companyName, String companyId, String entryDescription,
                                LocalDate effectiveDate, String odfiRouting, String discretionaryData,
                                String descriptiveDate) {
    }

    public record EntryInput(String name, String idNumber, String routing, String account, AccountType accountType,
                             boolean credit, boolean prenote, BigDecimal amount, String paymentType,
                             String addendaText) {
    }

    public static int transactionCode(AccountType type, boolean credit, boolean prenote) {
        if (type == AccountType.LOAN && !credit) {
            if (prenote) {
                throw new AchException("Loan accounts do not support debit prenotes.");
            }
            return 55;
        }
        return type.base + (credit ? (prenote ? 3 : 2) : (prenote ? 8 : 7));
    }

    // ---- file / batch / entry ----------------------------------------------------------

    public static ACHDocument newDocument(FileSettings settings) {
        LocalDateTime now = LocalDateTime.now();
        FileHeader header = new FileHeader();
        header.setPriorityCode("01");
        header.setImmediateDestination(routingField(settings.destinationRouting()));
        header.setImmediateOrigin(routingField(settings.origin()));
        header.setFileCreationDate(AchFormat.toDate(now.toLocalDate()));
        header.setFileCreationTime(now.format(DateTimeFormatter.ofPattern("HHmm")));
        header.setFileIdModifier(blankTo(settings.fileIdModifier(), "A").toUpperCase());
        header.setBlockingFactor("10");
        header.setFormatCode("1");
        header.setImmediateDestinationName(upper(settings.destinationName(), 23));
        header.setImmediateOriginName(upper(settings.originName(), 23));
        ACHDocument doc = new ACHDocument();
        doc.setFileHeader(header);
        doc.setFileControl(new FileControl());
        return doc;
    }

    public static ACHBatch newBatch(BatchSettings s) {
        if (!BUILDABLE_SEC.contains(s.sec())) {
            throw new AchException("New batches can be PPD, CCD, WEB or TEL.");
        }
        String odfi = digits(s.odfiRouting());
        if (odfi.length() < 8) {
            throw new AchException("Originating bank routing number must have at least 8 digits.");
        }
        GeneralBatchHeader header = new GeneralBatchHeader();
        header.setServiceClassCode("200");
        header.setCompanyName(upper(require(s.companyName(), "Company name"), 16));
        header.setCompanyDiscretionaryData(upper(s.discretionaryData(), 20));
        header.setCompanyID(fit(require(s.companyId(), "Company ID"), 10));
        header.setStandardEntryClassCode(s.sec());
        header.setCompanyEntryDescription(upper(require(s.entryDescription(), "Entry description"), 10));
        header.setCompanyDescriptiveDate(upper(s.descriptiveDate(), 6));
        header.setEffectiveEntryDate(AchFormat.toDate(s.effectiveDate() == null
                ? AchFormat.nextBusinessDay(LocalDate.now()) : s.effectiveDate()));
        header.setOriginatorStatusCode("1");
        header.setOriginatorDFIIdentifier(odfi.substring(0, 8));
        header.setBatchNumber(1);
        return new ACHBatch().setBatchHeader(header);
    }

    public static ACHBatchDetail newEntry(String sec, EntryInput in) {
        String routing = digits(in.routing());
        if (!AchFormat.isValidRouting(routing)) {
            throw new AchException("Routing number " + in.routing() + " is not a valid 9-digit ABA number.");
        }
        String account = require(in.account(), "Account number").trim();
        if (account.length() > 17) {
            throw new AchException("Account number can be at most 17 characters.");
        }
        BigDecimal amount = in.prenote() ? BigDecimal.ZERO : in.amount();
        if (amount == null || amount.signum() < 0 || amount.scale() > 2) {
            throw new AchException("Amount must be a positive number with at most 2 decimals.");
        }
        if (amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new AchException("Amount cannot exceed $99,999,999.99.");
        }

        NonIATEntryDetail entry = switch (sec) {
            case "PPD" -> new PPDEntryDetail()
                    .setIndividualName(upper(require(in.name(), "Name"), 22))
                    .setIdentificationNumber(upper(in.idNumber(), 15));
            case "CCD" -> new CCDEntryDetail()
                    .setReceivingCompanyName(upper(require(in.name(), "Receiving company name"), 22))
                    .setIdentificationNumber(upper(in.idNumber(), 15));
            case "WEB" -> new WEBEntryDetail()
                    .setIndividualName(upper(require(in.name(), "Name"), 22))
                    .setIdentificationNumber(upper(require(in.idNumber(), "Identification number"), 15))
                    .setPaymentTypeCode(blankTo(in.paymentType(), "S"));
            case "TEL" -> new TELEntryDetail()
                    .setIndividualName(upper(require(in.name(), "Name"), 22))
                    .setIdentificationNumber(upper(require(in.idNumber(), "Identification number"), 15))
                    .setPaymentTypeCode(blankTo(in.paymentType(), "S"));
            default -> throw new AchException("Entries can be added to PPD, CCD, WEB or TEL batches.");
        };
        entry.setDfiAccountNumber(account);
        EntryDetail e = entry;
        e.setTransactionCode(transactionCode(in.accountType(), in.credit(), in.prenote()));
        e.setReceivingDfiIdentification(routing.substring(0, 8));
        e.setCheckDigit((short) Character.digit(routing.charAt(8), 10));
        e.setAmount(amount);
        e.setTraceNumber(0L);

        ACHBatchDetail detail = new ACHBatchDetail().setDetailRecord(e);
        String addenda = in.addendaText() == null ? "" : in.addendaText().trim();
        if (!addenda.isEmpty()) {
            if ("TEL".equals(sec)) {
                throw new AchException("TEL entries cannot carry addenda.");
            }
            detail.addAddendaRecord(newAddenda(addenda));
        }
        e.setAddendaRecordIndicator((short) (detail.getAddendaRecords().isEmpty() ? 0 : 1));
        return detail;
    }

    public static GeneralAddendaRecord newAddenda(String text) {
        if (text.length() > 80) {
            throw new AchException("Addenda text can be at most 80 characters.");
        }
        GeneralAddendaRecord addenda = new GeneralAddendaRecord();
        addenda.setPaymentRelatedInformation(text);
        addenda.setAddendaSequenceNumber(1);
        addenda.setEntryDetailSequenceNumber(0L);
        return addenda;
    }

    /** Recalculates every control field and trace number so the document is ready to write. */
    public static void finish(ACHDocument doc) {
        AchControls.renumberTraces(doc);
        AchControls.recalculate(doc);
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Immediate origin/destination are 10 characters: a 9-digit routing number gets a leading blank. */
    static String routingField(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new AchException("Immediate origin and destination are required.");
        }
        if (v.length() > 10) {
            throw new AchException("\"" + v + "\" is longer than 10 characters.");
        }
        return " ".repeat(10 - v.length()) + v;
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new AchException(label + " is required.");
        }
        return value;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String upper(String value, int max) {
        return value == null || value.isBlank() ? null : fit(value.trim().toUpperCase(), max);
    }

    private static String fit(String value, int max) {
        String v = value.trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
