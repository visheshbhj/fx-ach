package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.fx.ach.core.AchInsert.Kind;
import com.fx.ach.core.AchInsert.Slot;
import com.fx.ach.core.AchInsert.Where;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
}
