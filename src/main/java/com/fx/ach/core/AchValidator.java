package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.afrunt.jach.domain.AddendaRecord;
import com.afrunt.jach.domain.BatchControl;
import com.afrunt.jach.domain.BatchHeader;
import com.afrunt.jach.domain.EntryDetail;
import com.afrunt.jach.domain.FileControl;
import com.afrunt.jach.domain.FileHeader;
import com.afrunt.jach.domain.addenda.GeneralAddendaRecord;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Business-rule checks that jACH's parser does not perform: totals, hashes, check digits, traces.
 */
public final class AchValidator {

    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String message, ACHRecord record) {
        @Override
        public String toString() {
            String where = record != null && record.getLineNumber() > 0 ? "Line " + record.getLineNumber() + ": " : "";
            return where + message;
        }
    }

    private AchValidator() {
    }

    public static List<Issue> validate(ACHDocument doc) {
        List<Issue> issues = new ArrayList<>();
        FileHeader fh = doc.getFileHeader();
        if (fh == null) {
            issues.add(new Issue(Severity.ERROR, "File header is missing.", null));
            return issues;
        }
        checkRoutingField(issues, fh, "Immediate destination", fh.getImmediateDestination(), true);
        checkRoutingField(issues, fh, "Immediate origin", fh.getImmediateOrigin(), false);
        if (doc.getBatches().isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "File contains no batches.", fh));
        }

        Set<Long> traces = new HashSet<>();
        int expectedBatch = 0;
        for (ACHBatch batch : doc.getBatches()) {
            expectedBatch++;
            validateBatch(issues, batch, expectedBatch, traces);
        }

        FileControl fc = doc.getFileControl();
        if (fc == null) {
            issues.add(new Issue(Severity.ERROR, "File control record (9) is missing.", null));
            return issues;
        }
        AchControls.Totals t = AchControls.totals(doc);
        mismatch(issues, fc, "File batch count", fc.getBatchCount(), doc.getBatches().size());
        mismatch(issues, fc, "File entry/addenda count", fc.getEntryAddendaCount(), t.entryAddendaCount());
        mismatch(issues, fc, "File entry hash", fc.getEntryHashTotals() == null ? null : BigInteger.valueOf(fc.getEntryHashTotals()), t.hash());
        money(issues, fc, "File total debits", fc.getTotalDebits(), t.debits());
        money(issues, fc, "File total credits", fc.getTotalCredits(), t.credits());
        mismatch(issues, fc, "File block count", fc.getBlockCount(), AchControls.blockCount(doc));
        return issues;
    }

    private static void validateBatch(List<Issue> issues, ACHBatch batch, int expectedNumber, Set<Long> traces) {
        BatchHeader bh = batch.getBatchHeader();
        BatchControl bc = batch.getBatchControl();
        String odfi = AchFormat.trim(bh.getOriginatorDFIIdentifier());
        if (bh.getBatchNumber() == null || bh.getBatchNumber() != expectedNumber) {
            issues.add(new Issue(Severity.WARNING, "Batch number is " + bh.getBatchNumber()
                    + "; batches are usually numbered in ascending order (expected " + expectedNumber + ").", bh));
        }
        if (bh.getEffectiveEntryDate() == null) {
            issues.add(new Issue(Severity.ERROR, "Batch has no effective entry date.", bh));
        }
        if (batch.getDetails().isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "Batch " + expectedNumber + " has no entries.", bh));
        }

        AchControls.Totals t = AchControls.totals(batch);
        String serviceClass = AchFormat.trim(bh.getServiceClassCode());
        if ("220".equals(serviceClass) && t.debits().signum() > 0) {
            issues.add(new Issue(Severity.ERROR, "Batch is marked credits-only (220) but contains debits.", bh));
        }
        if ("225".equals(serviceClass) && t.credits().signum() > 0) {
            issues.add(new Issue(Severity.ERROR, "Batch is marked debits-only (225) but contains credits.", bh));
        }

        for (ACHBatchDetail detail : batch.getDetails()) {
            EntryDetail e = detail.getDetailRecord();
            String routing = AchFormat.trim(e.getReceivingDfiIdentification())
                    + (e.getCheckDigit() == null ? "" : e.getCheckDigit());
            if (!AchFormat.isValidRouting(routing)) {
                issues.add(new Issue(Severity.ERROR, "Receiving routing number " + routing + " fails the check-digit test.", e));
            }
            Integer code = e.getTransactionCode();
            if (code == null || !AchCodes.TRANSACTION.containsKey(String.format("%02d", code))) {
                issues.add(new Issue(Severity.ERROR, "Unknown transaction code " + code + ".", e));
            } else if (AchCodes.isPrenote(code) && e.getAmount() != null && e.getAmount().signum() != 0) {
                issues.add(new Issue(Severity.ERROR, "Prenote entries must have a zero amount.", e));
            }
            boolean hasAddenda = !detail.getAddendaRecords().isEmpty();
            boolean flagged = e.getAddendaRecordIndicator() != null && e.getAddendaRecordIndicator() == 1;
            if (hasAddenda != flagged) {
                issues.add(new Issue(Severity.ERROR, "Addenda indicator is " + (flagged ? "1" : "0") + " but the entry has "
                        + detail.getAddendaRecords().size() + " addenda record(s).", e));
            }
            Long trace = e.getTraceNumber();
            if (trace != null) {
                String traceStr = String.format("%015d", trace);
                if (!odfi.isEmpty() && !traceStr.startsWith(odfi)) {
                    issues.add(new Issue(Severity.WARNING, "Trace number " + traceStr + " does not start with the originating bank "
                            + odfi + ".", e));
                }
                if (!traces.add(trace)) {
                    issues.add(new Issue(Severity.ERROR, "Duplicate trace number " + traceStr + ".", e));
                }
                for (AddendaRecord a : detail.getAddendaRecords()) {
                    if (a instanceof GeneralAddendaRecord g && g.getEntryDetailSequenceNumber() != null
                            && g.getEntryDetailSequenceNumber() != trace % 10_000_000L) {
                        issues.add(new Issue(Severity.WARNING, "Addenda entry sequence " + g.getEntryDetailSequenceNumber()
                                + " does not match its entry's trace number.", a));
                    }
                }
            }
        }

        if (bc == null) {
            issues.add(new Issue(Severity.ERROR, "Batch " + expectedNumber + " has no batch control record (8).", bh));
            return;
        }
        mismatch(issues, bc, "Batch entry/addenda count", bc.getEntryAddendaCount(), t.entryAddendaCount());
        mismatch(issues, bc, "Batch entry hash", bc.getEntryHash(), t.hash());
        money(issues, bc, "Batch total debits", bc.getTotalDebits(), t.debits());
        money(issues, bc, "Batch total credits", bc.getTotalCredits(), t.credits());
        mismatch(issues, bc, "Batch control service class", bc.getServiceClassCode() == null ? null : String.valueOf(bc.getServiceClassCode()), serviceClass);
        mismatch(issues, bc, "Batch control company ID", AchFormat.trim(bc.getCompanyIdentification()), AchFormat.trim(AchControls.companyId(bh)));
        mismatch(issues, bc, "Batch control batch number", bc.getBatchNumber(), bh.getBatchNumber());
        mismatch(issues, bc, "Batch control originating bank", AchFormat.trim(bc.getOriginatingDfiIdentification()), odfi);
    }

    private static void checkRoutingField(List<Issue> issues, ACHRecord r, String label, String value, boolean mustBeRouting) {
        String v = AchFormat.trim(value);
        if (v.length() == 9 && v.chars().allMatch(Character::isDigit)) {
            if (!AchFormat.isValidRouting(v)) {
                issues.add(new Issue(Severity.WARNING, label + " " + v + " fails the routing check-digit test.", r));
            }
        } else if (mustBeRouting) {
            issues.add(new Issue(Severity.WARNING, label + " \"" + v + "\" is not a 9-digit routing number.", r));
        }
    }

    private static void mismatch(List<Issue> issues, ACHRecord r, String label, Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            issues.add(new Issue(Severity.ERROR, label + " is " + actual + " but should be " + expected + ".", r));
        }
    }

    private static void money(List<Issue> issues, ACHRecord r, String label, BigDecimal actual, BigDecimal expected) {
        if (actual == null || actual.compareTo(expected) != 0) {
            issues.add(new Issue(Severity.ERROR, label + " is " + AchFormat.money(actual) + " but entries add up to "
                    + AchFormat.money(expected) + ".", r));
        }
    }
}
