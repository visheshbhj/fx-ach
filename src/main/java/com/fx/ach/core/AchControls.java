package com.fx.ach.core;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.AddendaRecord;
import com.afrunt.jach.domain.BatchControl;
import com.afrunt.jach.domain.BatchHeader;
import com.afrunt.jach.domain.EntryDetail;
import com.afrunt.jach.domain.FileControl;
import com.afrunt.jach.domain.addenda.GeneralAddendaRecord;
import com.afrunt.jach.domain.addenda.ReturnAddendaRecord;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Computes the totals, counts and hashes that batch and file control records must carry.
 */
public final class AchControls {

    private static final BigInteger HASH_MODULUS = BigInteger.TEN.pow(10);

    private AchControls() {
    }

    /** Totals for one batch or the whole file. */
    public record Totals(int entries, int addenda, BigInteger hash, BigDecimal debits, BigDecimal credits) {
        public int entryAddendaCount() {
            return entries + addenda;
        }
    }

    public static Totals totals(ACHBatch batch) {
        int entries = 0;
        int addenda = 0;
        BigInteger hash = BigInteger.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (ACHBatchDetail detail : batch.getDetails()) {
            EntryDetail entry = detail.getDetailRecord();
            entries++;
            addenda += detail.getAddendaRecords().size();
            String rdfi = entry.getReceivingDfiIdentification();
            if (rdfi != null && !rdfi.isBlank() && rdfi.trim().chars().allMatch(Character::isDigit)) {
                hash = hash.add(new BigInteger(rdfi.trim()));
            }
            BigDecimal amount = entry.getAmount() == null ? BigDecimal.ZERO : entry.getAmount();
            int code = entry.getTransactionCode() == null ? 0 : entry.getTransactionCode();
            if (AchCodes.isDebit(code)) {
                debits = debits.add(amount);
            } else if (AchCodes.isCredit(code)) {
                credits = credits.add(amount);
            }
        }
        return new Totals(entries, addenda, hash.mod(HASH_MODULUS), debits, credits);
    }

    public static Totals totals(ACHDocument doc) {
        int entries = 0;
        int addenda = 0;
        BigInteger hash = BigInteger.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (ACHBatch batch : doc.getBatches()) {
            Totals t = totals(batch);
            entries += t.entries();
            addenda += t.addenda();
            hash = hash.add(t.hash());
            debits = debits.add(t.debits());
            credits = credits.add(t.credits());
        }
        return new Totals(entries, addenda, hash.mod(HASH_MODULUS), debits, credits);
    }

    /** Number of physical records the file will have, excluding 9-filler padding. */
    public static int recordCount(ACHDocument doc) {
        int lines = 2;
        for (ACHBatch batch : doc.getBatches()) {
            Totals t = totals(batch);
            lines += 2 + t.entryAddendaCount();
        }
        return lines;
    }

    public static int blockCount(ACHDocument doc) {
        return (recordCount(doc) + 9) / 10;
    }

    /** Service class code a batch should carry given its content. */
    public static String serviceClassFor(Totals t, String current) {
        boolean hasDebits = t.debits().signum() > 0;
        boolean hasCredits = t.credits().signum() > 0;
        if (hasDebits && hasCredits) {
            return "200";
        } else if (hasDebits) {
            return "225";
        } else if (hasCredits) {
            return "220";
        }
        return current == null || current.isBlank() ? "200" : current;
    }

    /**
     * Rewrites every batch control and the file control so counts, hashes and totals are correct.
     * Batches are renumbered 1..n and service class codes are kept consistent with content.
     */
    public static void recalculate(ACHDocument doc) {
        int batchNumber = 0;
        for (ACHBatch batch : doc.getBatches()) {
            batchNumber++;
            BatchHeader header = batch.getBatchHeader();
            Totals t = totals(batch);
            String serviceClass = serviceClassFor(t, header.getServiceClassCode());
            header.setServiceClassCode(serviceClass);
            header.setBatchNumber(batchNumber);
            for (ACHBatchDetail detail : batch.getDetails()) {
                detail.getDetailRecord().setAddendaRecordIndicator((short) (detail.getAddendaRecords().isEmpty() ? 0 : 1));
            }

            BatchControl control = batch.getBatchControl() == null ? new BatchControl() : batch.getBatchControl();
            control.setServiceClassCode(Integer.valueOf(serviceClass));
            control.setEntryAddendaCount(t.entryAddendaCount());
            control.setEntryHash(t.hash());
            control.setTotalDebits(t.debits());
            control.setTotalCredits(t.credits());
            control.setCompanyIdentification(companyId(header));
            control.setOriginatingDfiIdentification(header.getOriginatorDFIIdentifier());
            control.setBatchNumber(batchNumber);
            batch.setBatchControl(control);
        }

        Totals t = totals(doc);
        FileControl fc = doc.getFileControl() == null ? new FileControl() : doc.getFileControl();
        fc.setBatchCount(doc.getBatches().size());
        fc.setBlockCount(blockCount(doc));
        fc.setEntryAddendaCount(t.entryAddendaCount());
        fc.setEntryHashTotals(t.hash().longValue());
        fc.setTotalDebits(t.debits());
        fc.setTotalCredits(t.credits());
        doc.setFileControl(fc);
    }

    /**
     * Assigns trace numbers ODFI + running sequence (unique within the file) and links each
     * payment-related addenda back to its entry.
     */
    public static void renumberTraces(ACHDocument doc) {
        long sequence = 0;
        for (ACHBatch batch : doc.getBatches()) {
            String odfi = batch.getBatchHeader().getOriginatorDFIIdentifier();
            long prefix = odfi == null || odfi.isBlank() ? 0 : Long.parseLong(odfi.trim());
            for (ACHBatchDetail detail : batch.getDetails()) {
                sequence++;
                detail.getDetailRecord().setTraceNumber(prefix * 10_000_000L + sequence);
                int addendaSeq = 0;
                for (AddendaRecord addenda : detail.getAddendaRecords()) {
                    if (addenda instanceof GeneralAddendaRecord general) {
                        general.setAddendaSequenceNumber(++addendaSeq);
                        general.setEntryDetailSequenceNumber(sequence);
                    } else if (addenda instanceof ReturnAddendaRecord ret) {
                        ret.setTraceNumber(BigInteger.valueOf(detail.getDetailRecord().getTraceNumber()));
                    }
                }
            }
        }
    }

    public static String companyId(BatchHeader header) {
        if (header instanceof com.afrunt.jach.domain.GeneralBatchHeader general) {
            return general.getCompanyID();
        }
        if (header instanceof com.afrunt.jach.domain.IATBatchHeader iat) {
            return iat.getOriginatorID();
        }
        return null;
    }
}
