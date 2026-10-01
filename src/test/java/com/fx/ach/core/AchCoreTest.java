package com.fx.ach.core;

import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.EntryDetail;
import com.afrunt.jach.domain.FileControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchCoreTest {

    private final AchService service = new AchService();

    @ParameterizedTest
    @EnumSource(AchTemplates.BuiltIn.class)
    void templatesRoundTripWithoutErrors(AchTemplates.BuiltIn template) {
        ACHDocument doc = AchTemplates.create(template, AchTemplates.sampleOrigin());
        String text = service.write(doc);
        for (String line : text.split("\\R")) {
            assertEquals(94, line.length(), line);
        }
        assertEquals(0, text.split("\\R").length % 10, "padded to whole blocks");

        ACHDocument reread = service.read(text).document();
        List<AchValidator.Issue> errors = AchValidator.validate(reread).stream()
                .filter(i -> i.severity() == AchValidator.Severity.ERROR).toList();
        assertTrue(errors.isEmpty(), errors.toString());
    }

    @Test
    void payrollTotalsAreComputed() {
        ACHDocument doc = AchTemplates.create(AchTemplates.BuiltIn.PAYROLL, AchTemplates.sampleOrigin());
        FileControl fc = doc.getFileControl();
        assertEquals(0, new BigDecimal("7555.65").compareTo(fc.getTotalCredits()));
        assertEquals(0, BigDecimal.ZERO.compareTo(fc.getTotalDebits()));
        assertEquals(3, fc.getEntryAddendaCount());
        // 01100001 + 02100002 + 02600959
        assertEquals(5_800_962L, fc.getEntryHashTotals());
        assertEquals("220", doc.getBatches().get(0).getBatchHeader().getServiceClassCode());
    }

    @Test
    void editingAFieldProducesReadableMeaningAndDetectsMismatch() {
        ACHDocument doc = service.normalise(AchTemplates.create(AchTemplates.BuiltIn.PAYROLL, AchTemplates.sampleOrigin()));
        EntryDetail entry = doc.getBatches().get(0).getDetails().get(0).getDetailRecord();
        FieldView amount = service.fields(entry).stream().filter(f -> f.name().equals("Total Amount")).findFirst().orElseThrow();
        assertEquals("2450.00", amount.value());
        assertEquals("$2,450.00", amount.meaning());

        AchService.replace(doc, entry, service.withField(entry, amount, "100.25"));
        assertTrue(AchValidator.validate(doc).stream().anyMatch(i -> i.message().startsWith("Batch total credits")));

        AchControls.recalculate(doc);
        assertTrue(AchValidator.validate(doc).stream().noneMatch(i -> i.severity() == AchValidator.Severity.ERROR));
    }

    @Test
    void tolerantReadingOfStrippedLines() {
        String text = service.write(AchTemplates.create(AchTemplates.BuiltIn.VENDOR, AchTemplates.sampleOrigin()), false);
        String stripped = String.join("\r\n", text.lines().map(String::stripTrailing).toList());
        AchService.ReadResult result = service.read(stripped);
        assertEquals(2, result.document().getBatches().get(0).getDetails().size());
        assertTrue(result.notes().get(0).contains("padded"));
    }

    @Test
    void transactionCodes() {
        assertEquals(22, AchBuilder.transactionCode(AchBuilder.AccountType.CHECKING, true, false));
        assertEquals(38, AchBuilder.transactionCode(AchBuilder.AccountType.SAVINGS, false, true));
        assertEquals(55, AchBuilder.transactionCode(AchBuilder.AccountType.LOAN, false, false));
    }

    @Test
    void routingCheckDigit() {
        assertTrue(AchFormat.isValidRouting("021000021"));
        assertTrue(!AchFormat.isValidRouting("021000022"));
        assertThrows(AchException.class, () -> AchBuilder.newEntry("PPD", new AchBuilder.EntryInput("A", "", "021000022",
                "1", AchBuilder.AccountType.CHECKING, true, false, BigDecimal.ONE, null, null)));
    }

    @Test
    void everyFieldOfEveryRecordIsDescribed() {
        ACHDocument doc = service.normalise(AchTemplates.create(AchTemplates.BuiltIn.VENDOR, AchTemplates.sampleOrigin()));
        List<String> names = new ArrayList<>();
        AchService.records(doc).forEach(r -> service.fields(r).forEach(f -> {
            assertEquals(f.length(), f.raw().length(), f.name());
            names.add(f.name());
        }));
        assertTrue(names.contains("Payment Related Information"));
    }

    @Test
    void reportListsEveryEntryAndEscapesText() {
        ACHDocument doc = AchTemplates.create(AchTemplates.BuiltIn.VENDOR, AchTemplates.sampleOrigin());
        String html = AchReport.html(service, doc, "<vendor>.ach");
        assertTrue(html.contains("GLOBEX SUPPLY"));
        assertTrue(html.contains("$12,500.00"));
        assertTrue(html.contains("&lt;vendor&gt;.ach"));
        assertTrue(html.contains("No problems found"));
    }

    @Test
    void sampleFilesInRepoAreValid() throws Exception {
        for (String name : List.of("payroll.ach", "vendor.ach", "consumer_debit.ach", "returns.ach")) {
            ACHDocument doc = service.read(java.nio.file.Path.of("samples", name)).document();
            assertTrue(AchValidator.validate(doc).isEmpty(), name + ": " + AchValidator.validate(doc));
        }
    }
}
