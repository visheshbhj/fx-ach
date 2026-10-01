package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.afrunt.jach.domain.AddendaRecord;

import java.util.Optional;

/**
 * Works out where a new record can go relative to an existing one, and puts it there.
 */
public final class AchInsert {

    public enum Kind {
        ENTRY("Entry"), ADDENDA("Addenda"), BATCH("Batch");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public enum Where { BEFORE, AFTER }

    /** Insertion point: batch index, entry index within the batch, addenda index within the entry. */
    public record Slot(Kind kind, int batch, int detail, int addenda) {
    }

    /** Where a record sits in the document. Indices are -1 when not applicable. */
    record Location(Type type, int batch, int detail, int addenda) {
        enum Type { FILE_HEADER, BATCH_HEADER, ENTRY, ADDENDA, BATCH_CONTROL, FILE_CONTROL }
    }

    private AchInsert() {
    }

    static Location locate(ACHDocument doc, ACHRecord record) {
        if (record == doc.getFileHeader()) {
            return new Location(Location.Type.FILE_HEADER, -1, -1, -1);
        }
        if (record == doc.getFileControl()) {
            return new Location(Location.Type.FILE_CONTROL, -1, -1, -1);
        }
        for (int b = 0; b < doc.getBatches().size(); b++) {
            ACHBatch batch = doc.getBatches().get(b);
            if (batch.getBatchHeader() == record) {
                return new Location(Location.Type.BATCH_HEADER, b, -1, -1);
            }
            if (batch.getBatchControl() == record) {
                return new Location(Location.Type.BATCH_CONTROL, b, -1, -1);
            }
            for (int d = 0; d < batch.getDetails().size(); d++) {
                ACHBatchDetail detail = batch.getDetails().get(d);
                if (detail.getDetailRecord() == record) {
                    return new Location(Location.Type.ENTRY, b, d, -1);
                }
                int a = detail.getAddendaRecords().indexOf(record);
                if (a >= 0) {
                    return new Location(Location.Type.ADDENDA, b, d, a);
                }
            }
        }
        throw new AchException("Record not found in document.");
    }

    /** The slot for a new record of {@code kind} placed before/after {@code anchor}, if that is legal. */
    public static Optional<Slot> slot(ACHDocument doc, ACHRecord anchor, Kind kind, Where where) {
        Location loc = locate(doc, anchor);
        boolean before = where == Where.BEFORE;
        return switch (kind) {
            case BATCH -> switch (loc.type()) {
                case FILE_HEADER -> before ? Optional.empty() : Optional.of(new Slot(kind, 0, -1, -1));
                case FILE_CONTROL -> before ? Optional.of(new Slot(kind, doc.getBatches().size(), -1, -1)) : Optional.empty();
                // anywhere inside a batch: the new batch goes before/after that whole batch
                default -> Optional.of(new Slot(kind, before ? loc.batch() : loc.batch() + 1, -1, -1));
            };
            case ENTRY -> switch (loc.type()) {
                case BATCH_HEADER -> before ? Optional.empty() : Optional.of(new Slot(kind, loc.batch(), 0, -1));
                case BATCH_CONTROL -> before
                        ? Optional.of(new Slot(kind, loc.batch(), doc.getBatches().get(loc.batch()).getDetails().size(), -1))
                        : Optional.empty();
                case ENTRY -> Optional.of(new Slot(kind, loc.batch(), before ? loc.detail() : loc.detail() + 1, -1));
                case ADDENDA -> {
                    // an entry may only follow the last addenda of the previous entry
                    int count = doc.getBatches().get(loc.batch()).getDetails().get(loc.detail()).getAddendaRecords().size();
                    yield !before && loc.addenda() == count - 1
                            ? Optional.of(new Slot(kind, loc.batch(), loc.detail() + 1, -1)) : Optional.empty();
                }
                default -> Optional.empty();
            };
            case ADDENDA -> switch (loc.type()) {
                case ENTRY -> before ? Optional.empty() : Optional.of(new Slot(kind, loc.batch(), loc.detail(), 0));
                case ADDENDA -> Optional.of(new Slot(kind, loc.batch(), loc.detail(), before ? loc.addenda() : loc.addenda() + 1));
                default -> Optional.empty();
            };
        };
    }

    /** The batch that a new entry/addenda in this slot belongs to. */
    public static ACHBatch batchOf(ACHDocument doc, Slot slot) {
        return slot.kind() == Kind.BATCH ? null : doc.getBatches().get(slot.batch());
    }

    public static void insert(ACHDocument doc, Slot slot, ACHBatch batch) {
        doc.getBatches().add(slot.batch(), batch);
    }

    public static void insert(ACHDocument doc, Slot slot, ACHBatchDetail detail) {
        doc.getBatches().get(slot.batch()).getDetails().add(slot.detail(), detail);
    }

    public static void insert(ACHDocument doc, Slot slot, AddendaRecord addenda) {
        doc.getBatches().get(slot.batch()).getDetails().get(slot.detail()).getAddendaRecords().add(slot.addenda(), addenda);
    }

    /** After insertion (and any re-parse), the record that now occupies the slot. */
    public static ACHRecord recordAt(ACHDocument doc, Slot slot) {
        ACHBatch batch = doc.getBatches().get(slot.batch());
        return switch (slot.kind()) {
            case BATCH -> batch.getBatchHeader();
            case ENTRY -> batch.getDetails().get(slot.detail()).getDetailRecord();
            case ADDENDA -> batch.getDetails().get(slot.detail()).getAddendaRecords().get(slot.addenda());
        };
    }
}
