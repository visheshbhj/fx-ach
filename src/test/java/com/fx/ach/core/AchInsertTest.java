package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.fx.ach.core.AchInsert.Kind;
import com.fx.ach.core.AchInsert.Slot;
import com.fx.ach.core.AchInsert.Where;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchInsertTest {

    private final AchService service = new AchService();
    private final ACHDocument doc = service.normalise(AchTemplates.create(AchTemplates.BuiltIn.VENDOR, AchTemplates.sampleOrigin()));
    private final ACHBatch batch = doc.getBatches().get(0);

    @Test
    void slotsRespectFileStructure() {
        ACHRecord header = doc.getFileHeader();
        assertTrue(AchInsert.slot(doc, header, Kind.ENTRY, Where.AFTER).isEmpty());
        assertEquals(Optional.of(new Slot(Kind.BATCH, 0, -1, -1)), AchInsert.slot(doc, header, Kind.BATCH, Where.AFTER));
        assertTrue(AchInsert.slot(doc, header, Kind.BATCH, Where.BEFORE).isEmpty());

        assertEquals(Optional.of(new Slot(Kind.ENTRY, 0, 0, -1)), AchInsert.slot(doc, batch.getBatchHeader(), Kind.ENTRY, Where.AFTER));
        assertEquals(Optional.of(new Slot(Kind.ENTRY, 0, 2, -1)), AchInsert.slot(doc, batch.getBatchControl(), Kind.ENTRY, Where.BEFORE));

        ACHRecord firstEntry = batch.getDetails().get(0).getDetailRecord();
        ACHRecord firstAddenda = batch.getDetails().get(0).getAddendaRecords().get(0);
        assertEquals(Optional.of(new Slot(Kind.ADDENDA, 0, 0, 0)), AchInsert.slot(doc, firstEntry, Kind.ADDENDA, Where.AFTER));
        assertTrue(AchInsert.slot(doc, firstAddenda, Kind.ENTRY, Where.BEFORE).isEmpty(), "would split entry from its addenda");
        assertEquals(Optional.of(new Slot(Kind.ENTRY, 0, 1, -1)), AchInsert.slot(doc, firstAddenda, Kind.ENTRY, Where.AFTER));
        assertEquals(Optional.of(new Slot(Kind.BATCH, 1, -1, -1)), AchInsert.slot(doc, firstEntry, Kind.BATCH, Where.AFTER));
        assertTrue(AchInsert.slot(doc, doc.getFileControl(), Kind.ADDENDA, Where.BEFORE).isEmpty());
    }

    @Test
    void insertedEntryLandsInPlaceAndTotalsFollow() {
        Slot slot = AchInsert.slot(doc, batch.getDetails().get(0).getDetailRecord(), Kind.ENTRY, Where.BEFORE).orElseThrow();
        AchInsert.insert(doc, slot, AchBuilder.newEntry("CCD", new AchBuilder.EntryInput("NEW CO", "", "011000015", "42",
                AchBuilder.AccountType.CHECKING, true, false, new BigDecimal("1.00"), null, null)));
        AchBuilder.finish(doc);
        ACHDocument reread = service.normalise(doc);

        ACHRecord inserted = AchInsert.recordAt(reread, slot);
        assertSame(reread.getBatches().get(0).getDetails().get(0).getDetailRecord(), inserted);
        assertEquals("NEW CO", AchSummary.entry(service, reread.getBatches().get(0).getDetails().get(0)).name());
        assertEquals(0, new BigDecimal("13361.40").compareTo(reread.getFileControl().getTotalCredits()));
        assertTrue(AchValidator.validate(reread).isEmpty(), AchValidator.validate(reread).toString());
    }

    @Test
    void lineIndexMatchesWhereRecordsLand() {
        List<ACHRecord> records = AchService.records(doc);
        ACHRecord firstEntry = batch.getDetails().get(0).getDetailRecord();
        assertEquals(records.indexOf(firstEntry), AchInsert.lineIndex(doc, new Slot(Kind.ENTRY, 0, 0, -1)));
        assertEquals(records.indexOf(batch.getBatchControl()), AchInsert.lineIndex(doc, new Slot(Kind.ENTRY, 0, 2, -1)));
        assertEquals(records.indexOf(firstEntry) + 2, AchInsert.lineIndex(doc, new Slot(Kind.ADDENDA, 0, 0, 1)));
        assertEquals(records.indexOf(doc.getFileControl()), AchInsert.lineIndex(doc, new Slot(Kind.BATCH, 1, -1, -1)));
    }

    @Test
    void pastedRawLineReplacesAndInserts() {
        String entryLine = batch.getDetails().get(1).getDetailRecord().getRecord();
        int index = AchService.records(doc).indexOf(batch.getDetails().get(1).getDetailRecord());

        // update: change the amount inside the pasted line (CRLF and stripped trailing spaces are tolerated)
        String edited = (entryLine.substring(0, 29) + "0000099999" + entryLine.substring(39)).stripTrailing() + "\r\n";
        ACHDocument updated = service.splice(doc, index, 1, edited);
        assertEquals(0, new BigDecimal("999.99").compareTo(updated.getBatches().get(0).getDetails().get(1).getDetailRecord().getAmount()));

        // insert: the same entry line again before the first entry
        ACHDocument inserted = service.splice(doc, 2, 0, entryLine);
        assertEquals(3, inserted.getBatches().get(0).getDetails().size());
        assertEquals("INITECH LLC", AchSummary.entry(service, inserted.getBatches().get(0).getDetails().get(0)).name());

        // a line that doesn't fit the structure is rejected by the parser
        assertThrows(AchException.class, () -> service.splice(doc, 1, 0, entryLine));
        assertThrows(AchException.class, () -> service.splice(doc, 2, 0, "hello"));
    }

    @Test
    void iatTemplateReadsAsInternational() {
        ACHDocument iat = service.normalise(AchTemplates.create(AchTemplates.BuiltIn.INTERNATIONAL, AchTemplates.sampleOrigin()));
        AchSummary.EntryInfo e = AchSummary.entry(service, iat.getBatches().get(0).getDetails().get(0));
        assertEquals("MAPLE LEAF SUPPLY INC", e.name());
        assertEquals("004012345678", e.account());
        assertEquals(8, iat.getBatches().get(0).getDetails().get(0).getAddendaRecords().size());
        assertTrue(AchSummary.batch(iat.getBatches().get(0)).companyName().contains("CA (USD→CAD)"));
        assertTrue(AchValidator.validate(iat).isEmpty(), AchValidator.validate(iat).toString());
    }
}
