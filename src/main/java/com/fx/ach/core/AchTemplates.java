package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.BatchHeader;
import com.afrunt.jach.domain.FileHeader;
import com.fx.ach.core.AchBuilder.AccountType;
import com.fx.ach.core.AchBuilder.BatchSettings;
import com.fx.ach.core.AchBuilder.EntryInput;
import com.fx.ach.core.AchBuilder.FileSettings;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Built-in starting points plus user templates (ordinary ACH files kept in a folder).
 */
public final class AchTemplates {

    /** Where "Save as template" stores files: ~/.ach-studio/templates */
    public static final Path USER_TEMPLATE_DIR = Path.of(System.getProperty("user.home"), ".ach-studio", "templates");

    public enum BuiltIn {
        PAYROLL("Payroll – PPD direct deposit credits", "PPD", "PAYROLL"),
        VENDOR("Vendor payments – CCD credits with remittance", "CCD", "VENDOR PAY"),
        CONSUMER_DEBIT("Customer collections – WEB debits", "WEB", "SUBSCRIPTN"),
        EMPTY("Empty file – one PPD batch, no entries", "PPD", "PAYMENT");

        public final String title;
        public final String sec;
        public final String description;

        BuiltIn(String title, String sec, String description) {
            this.title = title;
            this.sec = sec;
            this.description = description;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** Everything needed to stamp out a file from a template. */
    public record Origin(FileSettings file, String companyName, String companyId, String odfiRouting,
                         LocalDate effectiveDate) {
    }

    public static Origin sampleOrigin() {
        return new Origin(
                new FileSettings("076401251", "FEDERAL RESERVE BANK", "076401251", "MY BANK", "A"),
                "ACME CORP", "1234567890", "076401251",
                AchFormat.nextBusinessDay(LocalDate.now()));
    }

    private AchTemplates() {
    }

    public static ACHDocument create(BuiltIn template, Origin o) {
        ACHDocument doc = AchBuilder.newDocument(o.file());
        ACHBatch batch = AchBuilder.newBatch(new BatchSettings(template.sec, o.companyName(), o.companyId(),
                template.description, o.effectiveDate(), o.odfiRouting(), null, null));
        doc.addBatch(batch);
        switch (template) {
            case PAYROLL -> {
                add(batch, "PPD", "JANE DOE", "EMP-1001", "011000015", "123456789", AccountType.CHECKING, true, "2450.00", null);
                add(batch, "PPD", "JOHN SMITH", "EMP-1002", "021000021", "987654321", AccountType.CHECKING, true, "1980.55", null);
                add(batch, "PPD", "MARIA GARCIA", "EMP-1003", "026009593", "55500012", AccountType.SAVINGS, true, "3125.10", null);
            }
            case VENDOR -> {
                add(batch, "CCD", "GLOBEX SUPPLY", "V-2001", "011000015", "4000123456", AccountType.CHECKING, true, "12500.00",
                        "INV 10045 AND 10046 NET 30");
                add(batch, "CCD", "INITECH LLC", "V-2002", "021000021", "300045600", AccountType.CHECKING, true, "860.40",
                        "INV 7781");
            }
            case CONSUMER_DEBIT -> {
                add(batch, "WEB", "ALEX KIM", "CUST-501", "011000015", "11223344", AccountType.CHECKING, false, "29.99", null);
                add(batch, "WEB", "SAM PATEL", "CUST-502", "021000021", "99887766", AccountType.SAVINGS, false, "29.99", null);
            }
            case EMPTY -> {
            }
        }
        AchBuilder.finish(doc);
        return doc;
    }

    private static void add(ACHBatch batch, String sec, String name, String id, String routing, String account,
                            AccountType type, boolean credit, String amount, String addenda) {
        ACHBatchDetail detail = AchBuilder.newEntry(sec, new EntryInput(name, id, routing, account, type, credit, false,
                new BigDecimal(amount), "S", addenda));
        batch.addDetail(detail);
    }

    // ---- user templates ----------------------------------------------------------------

    public static List<Path> userTemplates() {
        if (!Files.isDirectory(USER_TEMPLATE_DIR)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(USER_TEMPLATE_DIR)) {
            return new ArrayList<>(files.filter(Files::isRegularFile).sorted().toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    public static Path saveUserTemplate(AchService service, ACHDocument doc, String name) throws IOException {
        Files.createDirectories(USER_TEMPLATE_DIR);
        String safe = name.trim().replaceAll("[^A-Za-z0-9 _.-]", "_");
        if (safe.isEmpty()) {
            throw new AchException("Template name is required.");
        }
        Path target = USER_TEMPLATE_DIR.resolve(safe.endsWith(".ach") ? safe : safe + ".ach");
        Files.writeString(target, service.write(doc, false), StandardCharsets.ISO_8859_1);
        return target;
    }

    /**
     * Loads a template file as a fresh document: creation date/time become now, effective dates
     * move to the given date, and (optionally) all amounts are zeroed so they can be filled in.
     */
    public static ACHDocument instantiate(AchService service, Path template, LocalDate effectiveDate,
                                          boolean clearAmounts) throws IOException {
        ACHDocument doc = service.read(template).document();
        LocalDateTime now = LocalDateTime.now();
        FileHeader header = doc.getFileHeader();
        header.setFileCreationDate(AchFormat.toDate(now.toLocalDate()));
        header.setFileCreationTime(now.format(DateTimeFormatter.ofPattern("HHmm")));
        for (ACHBatch batch : doc.getBatches()) {
            BatchHeader bh = batch.getBatchHeader();
            bh.setEffectiveEntryDate(AchFormat.toDate(effectiveDate));
            bh.setSettlementDate(null);
            if (clearAmounts) {
                batch.getDetails().forEach(d -> d.getDetailRecord().setAmount(BigDecimal.ZERO));
            }
        }
        AchBuilder.finish(doc);
        return doc;
    }
}
