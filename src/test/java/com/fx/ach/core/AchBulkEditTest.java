package com.fx.ach.core;

import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.afrunt.jach.domain.BatchHeader;
import com.afrunt.jach.domain.EntryDetail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchBulkEditTest {

    private final AchService service = new AchService();
    private final ACHDocument doc = twoBatches();

    /** A PPD payroll batch followed by a CCD vendor batch, so entries come in two record classes. */
    private ACHDocument twoBatches() {
        ACHDocument d = AchTemplates.create(AchTemplates.BuiltIn.PAYROLL, AchTemplates.sampleOrigin());
        ACHDocument vendor = AchTemplates.create(AchTemplates.BuiltIn.VENDOR, AchTemplates.sampleOrigin());
        d.getBatches().add(vendor.getBatches().get(0));
        AchBuilder.finish(d);
        return service.normalise(d);
    }

    private FieldView field(List<FieldView> fields, String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<ACHRecord> ofType(ACHDocument d, String recordTypeCode) {
        return AchService.records(d).stream().filter(r -> r.getRecordTypeCode().equals(recordTypeCode)).toList();
    }

    private List<ACHRecord> entries() {
        return ofType(doc, "6");
    }

    @Test
    void commonFieldsSpanEntriesOfDifferentSecCodes() {
        List<FieldView> common = AchBulkEdit.commonFields(service, entries());
        assertEquals(5, entries().size());
        assertTrue(common.stream().anyMatch(f -> f.name().equals("DFI Account Number")));
        assertTrue(common.stream().noneMatch(f -> f.name().equals("Record Type Code")), "constants are not offered");
    }

    @Test
    void setsOneAccountNumberOnEveryEntry() {
        FieldView account = field(AchBulkEdit.commonFields(service, entries()), "DFI Account Number");
        assertNull(AchBulkEdit.commonValue(service, entries(), account));

        AchBulkEdit.apply(service, doc, entries(), Map.of(account, "999888777"));
        AchControls.recalculate(doc);
        ACHDocument saved = service.normalise(doc);

        assertEquals("999888777", AchBulkEdit.commonValue(service, ofType(saved, "6"), account));
        assertTrue(AchValidator.validate(saved).stream().noneMatch(i -> i.severity() == AchValidator.Severity.ERROR));
    }

    @Test
    void changingTheOriginatingBankOfEveryBatchRenumbersTraces() {
        List<ACHRecord> headers = ofType(doc, "5");
        FieldView odfi = field(AchBulkEdit.commonFields(service, headers), "Originator DFI Identifier");
        Map<FieldView, String> edits = Map.of(odfi, "07640125");
        assertTrue(AchBulkEdit.changesOdfi(edits));

        AchBulkEdit.apply(service, doc, headers, edits);
        AchBuilder.finish(doc);
        ACHDocument saved = service.normalise(doc);

        for (var batch : saved.getBatches()) {
            BatchHeader bh = batch.getBatchHeader();
            assertEquals("07640125", bh.getOriginatorDFIIdentifier());
            assertEquals("07640125", batch.getBatchControl().getOriginatingDfiIdentification());
            for (var d : batch.getDetails()) {
                EntryDetail e = d.getDetailRecord();
                assertTrue(String.format("%015d", e.getTraceNumber()).startsWith("07640125"));
            }
        }
        assertTrue(AchValidator.validate(saved).isEmpty(), AchValidator.validate(saved).toString());
    }

    @Test
    void onlyTheChosenRecordsChange() {
        List<ACHRecord> picked = List.of(entries().get(0), entries().get(3));
        FieldView id = field(AchBulkEdit.commonFields(service, picked), "Identification Number");
        AchBulkEdit.apply(service, doc, picked, Map.of(id, "REF-1"));

        List<String> ids = entries().stream()
                .map(r -> service.fields(r).stream().filter(f -> f.name().equals("Identification Number")).findFirst().orElseThrow().value())
                .toList();
        assertEquals("REF-1", ids.get(0));
        assertEquals("REF-1", ids.get(3));
        assertFalse(ids.get(1).equals("REF-1"));
    }

    @Test
    void aValueThatDoesNotFitNamesTheLine() {
        FieldView account = field(AchBulkEdit.commonFields(service, entries()), "DFI Account Number");
        ACHRecord first = entries().get(0);
        AchException e = assertThrows(AchException.class,
                () -> AchBulkEdit.edited(service, first, Map.of(account, "X".repeat(30))));
        assertTrue(e.getMessage().startsWith("Line " + first.getLineNumber() + ": "), e.getMessage());
    }
}
