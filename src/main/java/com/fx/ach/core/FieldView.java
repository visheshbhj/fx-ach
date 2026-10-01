package com.fx.ach.core;

import com.afrunt.jach.metadata.ACHFieldMetadata;

/**
 * One field of a 94-character record, ready to be shown in a form.
 *
 * @param name     NACHA field name, e.g. "Transaction Code"
 * @param start    1-based start column (as printed in the NACHA spec)
 * @param end      1-based inclusive end column
 * @param raw      the exact characters in the record
 * @param value    the value a person would type to edit it (amounts as dollars, text trimmed)
 * @param meaning  plain-English interpretation of the value
 * @param inclusion M/R/O/blank requirement from the spec
 * @param editable false for record-type codes, constants and reserved filler
 */
public record FieldView(String name, int start, int end, String raw, String value, String meaning,
                        String inclusion, boolean editable, ACHFieldMetadata metadata) {

    public int length() {
        return end - start + 1;
    }

    public String position() {
        return start == end ? String.valueOf(start) : start + "–" + end;
    }
}
