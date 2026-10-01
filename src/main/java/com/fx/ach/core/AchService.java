package com.fx.ach.core;

import com.afrunt.jach.ACH;
import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.afrunt.jach.domain.AddendaRecord;
import com.afrunt.jach.domain.EntryDetail;
import com.afrunt.jach.domain.FileControl;
import com.afrunt.jach.domain.FileHeader;
import com.afrunt.jach.domain.RecordTypes;
import com.afrunt.jach.metadata.ACHBeanMetadata;
import com.afrunt.jach.metadata.ACHFieldMetadata;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin layer over jACH: tolerant reading, writing, and a field-by-field view of any record.
 */
public class AchService {

    public static final int RECORD_LENGTH = ACHRecord.ACH_RECORD_LENGTH;

    private final ACH ach = new ACH();

    /** Result of reading a file: the parsed document plus any clean-ups applied to the input. */
    public record ReadResult(ACHDocument document, List<String> notes) {
    }

    public ReadResult read(Path path) throws IOException {
        return read(Files.readString(path, StandardCharsets.ISO_8859_1));
    }

    /**
     * Parses ACH text. Real-world files often have trailing spaces stripped, CRLF endings,
     * no line breaks at all, or 9-filled padding lines; these are normalised before jACH parses.
     */
    public ReadResult read(String text) {
        List<String> notes = new ArrayList<>();
        List<String> lines = normalise(text, notes);
        if (lines.isEmpty()) {
            throw new AchException("The file is empty.");
        }
        String joined = String.join("\n", lines);
        try {
            return new ReadResult(ach.read(joined), notes);
        } catch (RuntimeException e) {
            throw new AchException(friendly(e), e);
        }
    }

    static List<String> normalise(String text, List<String> notes) {
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>();
        if (!t.contains("\n") && t.length() > RECORD_LENGTH && t.length() % RECORD_LENGTH == 0) {
            for (int i = 0; i < t.length(); i += RECORD_LENGTH) {
                lines.add(t.substring(i, i + RECORD_LENGTH));
            }
            notes.add("File had no line breaks; split into " + lines.size() + " records of 94 characters.");
            return lines;
        }
        int padded = 0;
        int lineNo = 0;
        for (String line : t.split("\n")) {
            lineNo++;
            if (line.isBlank()) {
                continue;
            }
            if (line.length() < RECORD_LENGTH) {
                line = line + " ".repeat(RECORD_LENGTH - line.length());
                padded++;
            } else if (line.length() > RECORD_LENGTH) {
                if (line.substring(RECORD_LENGTH).isBlank()) {
                    line = line.substring(0, RECORD_LENGTH);
                } else {
                    throw new AchException("Line " + lineNo + " is " + line.length()
                            + " characters long; ACH records must be exactly 94.");
                }
            }
            lines.add(line);
        }
        if (padded > 0) {
            notes.add(padded + " line(s) were shorter than 94 characters and were padded with spaces.");
        }
        return lines;
    }

    public String write(ACHDocument document) {
        return write(document, true);
    }

    public String write(ACHDocument document, boolean blockPadding) {
        try {
            ach.withBlockAligning(blockPadding);
            String text = ach.write(document);
            return text.endsWith("\n") ? text : text + System.lineSeparator();
        } catch (RuntimeException e) {
            throw new AchException(friendly(e), e);
        } finally {
            ach.withBlockAligning(false);
        }
    }

    /** Serialises and re-parses, so every record carries its canonical 94-char line and line number. */
    public ACHDocument normalise(ACHDocument document) {
        return read(write(document, false)).document();
    }

    // ---- field-level view --------------------------------------------------------------

    public ACHBeanMetadata metadata(ACHRecord record) {
        return ach.getMetadata().getBeanMetadata(record.getClass());
    }

    public String recordName(ACHRecord record) {
        return metadata(record).getACHRecordName();
    }

    /** The 94-character line for a record, formatted from its current values. */
    public String line(ACHRecord record) {
        ACHBeanMetadata bm = metadata(record);
        StringBuilder sb = new StringBuilder(" ".repeat(RECORD_LENGTH));
        for (ACHFieldMetadata fm : bm.getACHFieldsMetadata()) {
            Object value;
            try {
                value = fm.getGetter().invoke(record);
            } catch (ReflectiveOperationException e) {
                throw new AchException("Cannot read " + fm.getAchFieldName(), e);
            }
            String formatted = ach.getWriter().formatFieldValueAsString(value, bm, fm);
            if (formatted.length() > fm.getLength()) {
                throw new AchException(fm.getAchFieldName() + " is longer than " + fm.getLength() + " characters.");
            }
            sb.replace(fm.getStart(), fm.getEnd(), formatted);
        }
        return sb.toString();
    }

    public List<FieldView> fields(ACHRecord record) {
        String line = record.getRecord() != null ? record.getRecord() : line(record);
        List<FieldView> result = new ArrayList<>();
        for (ACHFieldMetadata fm : metadata(record).getACHFieldsMetadata()) {
            String raw = line.substring(fm.getStart(), fm.getEnd());
            boolean fixedConstant = fm.hasConstantValues() && fm.getValues().size() == 1;
            boolean editable = fm.getStart() > 0 && !fm.isReadOnly() && !fm.isBlank() && !fixedConstant;
            result.add(new FieldView(fm.getAchFieldName(), fm.getStart() + 1, fm.getEnd(), raw,
                    editValue(fm, raw), FieldMeanings.describe(fm.getAchFieldName(), raw, line),
                    inclusion(fm), editable, fm));
        }
        return result;
    }

    private static String inclusion(ACHFieldMetadata fm) {
        return switch (fm.getInclusionRequirement()) {
            case MANDATORY -> "Mandatory";
            case REQUIRED -> "Required";
            case OPTIONAL -> "Optional";
            case BLANK -> "Blank";
        };
    }

    private static boolean isAmount(ACHFieldMetadata fm) {
        return fm.isBigDecimal();
    }

    private static String editValue(ACHFieldMetadata fm, String raw) {
        if (isAmount(fm)) {
            BigDecimal amount = AchFormat.impliedCents(raw);
            return amount == null ? raw.trim() : amount.toPlainString();
        }
        return raw.trim();
    }

    /**
     * Returns a new record of the same type with one field changed. The value is entered the way
     * a person thinks of it (dollars for amounts, digits or text otherwise) and padded to width.
     */
    public ACHRecord withField(ACHRecord record, FieldView field, String input) {
        ACHFieldMetadata fm = field.metadata();
        String formatted = toFixedWidth(fm, input == null ? "" : input);
        String line = record.getRecord() != null ? record.getRecord() : line(record);
        String newLine = line.substring(0, fm.getStart()) + formatted + line.substring(fm.getEnd());
        try {
            ACHRecord updated = ach.getReader().readRecord(newLine, metadata(record));
            updated.setLineNumber(record.getLineNumber());
            return updated;
        } catch (RuntimeException e) {
            throw new AchException(field.name() + ": " + friendly(e), e);
        }
    }

    /** Re-parses a full 94-character line as the given record's type. */
    public ACHRecord parseLike(ACHRecord record, String line) {
        try {
            return ach.getReader().readRecord(line, metadata(record));
        } catch (RuntimeException e) {
            throw new AchException(friendly(e), e);
        }
    }

    static String toFixedWidth(ACHFieldMetadata fm, String input) {
        int len = fm.getLength();
        String v = input.trim();
        String out;
        if (v.isEmpty()) {
            out = fm.isNumber() && !fm.isOptional() ? "0".repeat(len) : " ".repeat(len);
        } else if (isAmount(fm)) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(v.replace(",", "").replace("$", ""));
            } catch (NumberFormatException e) {
                throw new AchException(fm.getAchFieldName() + ": \"" + input + "\" is not an amount.");
            }
            if (amount.signum() < 0 || amount.scale() > 2) {
                throw new AchException(fm.getAchFieldName() + ": amounts must be positive with at most 2 decimals.");
            }
            out = leftPad(amount.movePointRight(2).toBigInteger().toString(), len);
        } else if (fm.isNumber()) {
            if (!v.chars().allMatch(Character::isDigit)) {
                throw new AchException(fm.getAchFieldName() + " accepts digits only.");
            }
            out = leftPad(v, len);
        } else if (fm.isDate()) {
            out = toYymmdd(fm, v);
        } else {
            boolean rightJustified = fm.getAchFieldName().equals("Immediate Destination")
                    || fm.getAchFieldName().equals("Immediate Origin");
            String pad = " ".repeat(Math.max(0, len - v.length()));
            out = rightJustified ? pad + v : v + pad;
        }
        if (out.length() != len) {
            throw new AchException(fm.getAchFieldName() + " must fit in " + len + " characters (got "
                    + v.length() + ").");
        }
        return out;
    }

    private static String toYymmdd(ACHFieldMetadata fm, String v) {
        if (v.length() == 6 && AchFormat.parseYymmdd(v) != null) {
            return v;
        }
        try {
            return LocalDate.parse(v).format(DateTimeFormatter.ofPattern("yyMMdd"));
        } catch (RuntimeException e) {
            throw new AchException(fm.getAchFieldName() + ": enter a date as YYMMDD or YYYY-MM-DD.");
        }
    }

    private static String leftPad(String digits, int len) {
        return digits.length() >= len ? digits : "0".repeat(len - digits.length()) + digits;
    }

    // ---- document navigation ------------------------------------------------------------

    /** Every record of the document in file order. */
    public static List<ACHRecord> records(ACHDocument doc) {
        List<ACHRecord> out = new ArrayList<>();
        if (doc.getFileHeader() != null) {
            out.add(doc.getFileHeader());
        }
        for (ACHBatch batch : doc.getBatches()) {
            out.addAll(records(batch));
        }
        if (doc.getFileControl() != null) {
            out.add(doc.getFileControl());
        }
        return out;
    }

    /** Batch header, entries with their addenda, and batch control, in file order. */
    public static List<ACHRecord> records(ACHBatch batch) {
        List<ACHRecord> out = new ArrayList<>();
        out.add(batch.getBatchHeader());
        for (ACHBatchDetail detail : batch.getDetails()) {
            out.add(detail.getDetailRecord());
            out.addAll(detail.getAddendaRecords());
        }
        if (batch.getBatchControl() != null) {
            out.add(batch.getBatchControl());
        }
        return out;
    }

    /** Swaps one record object for another in place. */
    public static void replace(ACHDocument doc, ACHRecord old, ACHRecord updated) {
        if (doc.getFileHeader() == old) {
            doc.setFileHeader((FileHeader) updated);
            return;
        }
        if (doc.getFileControl() == old) {
            doc.setFileControl((FileControl) updated);
            return;
        }
        for (ACHBatch batch : doc.getBatches()) {
            if (batch.getBatchHeader() == old) {
                batch.setBatchHeader((com.afrunt.jach.domain.BatchHeader) updated);
                return;
            }
            if (batch.getBatchControl() == old) {
                batch.setBatchControl((com.afrunt.jach.domain.BatchControl) updated);
                return;
            }
            for (ACHBatchDetail detail : batch.getDetails()) {
                if (detail.getDetailRecord() == old) {
                    detail.setDetailRecord((EntryDetail) updated);
                    return;
                }
                List<AddendaRecord> addenda = detail.getAddendaRecords();
                for (int i = 0; i < addenda.size(); i++) {
                    if (addenda.get(i) == old) {
                        addenda.set(i, (AddendaRecord) updated);
                        return;
                    }
                }
            }
        }
        throw new AchException("Record not found in document.");
    }

    public static String recordTypeLabel(ACHRecord record) {
        for (RecordTypes t : RecordTypes.values()) {
            if (record.is(t)) {
                return AchCodes.describe(AchCodes.RECORD_TYPE, t.getRecordTypeCode());
            }
        }
        return "Record";
    }

    static String friendly(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && (t.getMessage() == null || t.getMessage().isBlank())) {
            t = t.getCause();
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return msg.replaceAll("\\s+", " ").trim();
    }
}
