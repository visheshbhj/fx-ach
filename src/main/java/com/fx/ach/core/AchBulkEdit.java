package com.fx.ach.core;

import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sets the same value in one or more fields of several records at once (records selected together
 * in the record panel), e.g. the originating bank of every batch header.
 */
public final class AchBulkEdit {

    /** Field names that hold the originating bank (ODFI); trace numbers are prefixed with it. */
    private static final List<String> ODFI_FIELDS = List.of("Originating DFI Identification", "Originator DFI Identifier");

    private AchBulkEdit() {
    }

    /**
     * The editable fields that every record has at the same columns under the same name, so one
     * value can be set across all of them (entries of different SEC codes share most columns).
     */
    public static List<FieldView> commonFields(AchService service, List<? extends ACHRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<FieldView> common = new ArrayList<>(service.fields(records.get(0)).stream().filter(FieldView::editable).toList());
        Map<Class<?>, Boolean> seen = new LinkedHashMap<>();
        for (ACHRecord r : records) {
            if (seen.put(r.getClass(), true) != null) {
                continue; // same record class means the same layout
            }
            List<FieldView> fields = service.fields(r);
            common.removeIf(c -> fields.stream().noneMatch(f -> f.editable() && sameColumn(f, c)));
        }
        return common;
    }

    /** The field's value when all records agree on it, otherwise null. */
    public static String commonValue(AchService service, List<? extends ACHRecord> records, FieldView field) {
        String value = null;
        for (ACHRecord r : records) {
            String v = find(service, r, field).value();
            if (value == null) {
                value = v;
            } else if (!value.equals(v)) {
                return null;
            }
        }
        return value;
    }

    /**
     * Applies the edits to every target record, replacing each in the document. Throws
     * {@link AchException} naming the first record a value does not fit; the document may then be
     * partly changed, so callers apply this inside an undoable commit.
     */
    public static void apply(AchService service, ACHDocument doc, List<? extends ACHRecord> targets, Map<FieldView, String> edits) {
        for (ACHRecord record : targets) {
            AchService.replace(doc, record, edited(service, record, edits));
        }
    }

    /** The record with the edits applied, without touching the document (also used to check values up front). */
    public static ACHRecord edited(AchService service, ACHRecord record, Map<FieldView, String> edits) {
        ACHRecord updated = record;
        try {
            for (Map.Entry<FieldView, String> edit : edits.entrySet()) {
                updated = service.withField(updated, find(service, updated, edit.getKey()), edit.getValue());
            }
        } catch (AchException e) {
            String where = record.getLineNumber() > 0 ? "Line " + record.getLineNumber() + ": " : "";
            throw new AchException(where + e.getMessage(), e);
        }
        return updated;
    }

    /** True when the edits change the originating bank, so trace numbers should be renumbered. */
    public static boolean changesOdfi(Map<FieldView, String> edits) {
        return edits.keySet().stream().anyMatch(f -> ODFI_FIELDS.contains(f.name()));
    }

    private static FieldView find(AchService service, ACHRecord record, FieldView like) {
        return service.fields(record).stream().filter(f -> sameColumn(f, like)).findFirst()
                .orElseThrow(() -> new AchException(like.name() + " is not a field of " + service.recordName(record) + "."));
    }

    private static boolean sameColumn(FieldView a, FieldView b) {
        return a.start() == b.start() && a.end() == b.end() && Objects.equals(a.name(), b.name());
    }
}
