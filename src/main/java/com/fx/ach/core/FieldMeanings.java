package com.fx.ach.core;

import java.math.BigDecimal;

/**
 * Explains a single raw field value in plain English, keyed by the NACHA field name.
 */
public final class FieldMeanings {

    private FieldMeanings() {
    }

    /**
     * @param name field name as published by jACH metadata
     * @param raw  exact characters of the field
     * @param line the full 94-character record, for fields whose meaning depends on neighbours
     */
    public static String describe(String name, String raw, String line) {
        String v = raw.trim();
        switch (name) {
            case "Record Type Code":
                return AchCodes.describe(AchCodes.RECORD_TYPE, v);
            case "Service Class Code":
                return AchCodes.describe(AchCodes.SERVICE_CLASS, v);
            case "Standard Entry Class Code":
                return AchCodes.describe(AchCodes.SEC, v);
            case "Transaction Code":
                return AchCodes.describe(AchCodes.TRANSACTION, v);
            case "Addenda Type Code":
                return AchCodes.describe(AchCodes.ADDENDA_TYPE, v);
            case "Return Reason Code":
                return AchCodes.describe(AchCodes.RETURN_REASON, v);
            case "Change Code":
                return AchCodes.describe(AchCodes.CHANGE_CODE, v);
            case "Total Amount":
            case "Total Debits":
            case "Total Credits":
            case "Foreign Payment Amount": {
                BigDecimal amount = AchFormat.impliedCents(raw);
                return amount == null ? "Not a number" : AchFormat.money(amount);
            }
            case "File Creation Date":
            case "Effective Entry Date":
            case "Date Of Death":
                return v.isEmpty() ? "" : orInvalid(AchFormat.yymmdd(v), "Not a valid YYMMDD date");
            case "Company Descriptive Date":
                return AchFormat.parseYymmdd(v) != null ? AchFormat.yymmdd(v) : (v.isEmpty() ? "" : "Free text shown to receiver");
            case "File Creation Time":
                return v.isEmpty() ? "" : orInvalid(AchFormat.hhmm(v), "Not a valid HHMM time");
            case "Immediate Destination":
                return AchFormat.describeRouting(v) + " · bank or operator receiving the file";
            case "Immediate Origin":
                return (v.length() == 9 ? AchFormat.describeRouting(v) : "ID " + v) + " · sender of the file";
            case "Receiving DFI Identification":
            case "Original Receiving DFI Identification":
                return "First 8 digits of receiver's bank routing number";
            case "Check Digit": {
                String routing = line.substring(3, 12);
                return AchFormat.isValidRouting(routing)
                        ? "Valid · full routing " + routing
                        : "INVALID for " + routing.substring(0, 8) + " (expected "
                        + safeCheckDigit(routing.substring(0, 8)) + ")";
            }
            case "DFI Account Number":
                return "Receiver's account number";
            case "Addenda Record Indicator":
                return "1".equals(v) ? "Yes – addenda record(s) follow" : "No addenda";
            case "Priority Code":
                return "01".equals(v) ? "Standard (always 01)" : "Expected 01";
            case "Record Size":
                return "Each record is 94 characters";
            case "Blocking Factor":
                return "Records per block (always 10)";
            case "Format Code":
                return "1".equals(v) ? "NACHA format version 1" : "Expected 1";
            case "File ID Modifier":
                return "Distinguishes multiple files sent the same day (A–Z, 0–9)";
            case "Originator Status Code":
                return switch (v) {
                    case "0" -> "ADV file prepared by an ACH operator";
                    case "1" -> "Originating bank is bound by NACHA rules";
                    case "2" -> "Federal government entity";
                    default -> "Unknown status " + v;
                };
            case "Originator DFI Identifier":
            case "Originating DFI Identification":
                return "First 8 digits of the originating bank's routing number";
            case "Settlement Date":
                return v.isEmpty() ? "Left blank; filled in by the ACH operator" : "Julian day " + v;
            case "Payment Type Code":
                return switch (v) {
                    case "R" -> "Recurring payment";
                    case "S" -> "Single payment";
                    case "ST" -> "Standing authorization";
                    case "" -> "";
                    default -> v;
                };
            case "Trace Number":
            case "Original Entry Trace Number":
                return v.length() == 15
                        ? "Bank " + v.substring(0, 8) + " · sequence " + Long.parseLong(v.substring(8))
                        : v;
            case "Entry Hash":
                return "Sum of receiving bank routing numbers (last 10 digits)";
            case "Entry Addenda Count":
                return number(v) + " entry and addenda records";
            case "Batch Count":
                return number(v) + " batch(es)";
            case "Block Count":
                return number(v) + " block(s) of 10 records";
            case "Batch Number":
                return "Batch #" + number(v);
            case "Addenda Sequence Number":
                return "Addenda #" + number(v) + " for this entry";
            case "Entry Detail Sequence Number":
                return "Matches the last 7 digits of the entry's trace number";
            case "Company Entry Description":
                return "Shown on the receiver's bank statement";
            case "Company Name":
                return "Originator name shown on the receiver's statement";
            case "Company ID":
            case "Company Identification":
                return "Originator's company ID (often 1 + EIN)";
            case "Individual Name":
            case "Receiving Company Name":
                return "Receiver";
            case "Identification Number":
                return "Originator's reference for the receiver";
            default:
                return "";
        }
    }

    private static String orInvalid(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    private static String number(String v) {
        try {
            return String.valueOf(Long.parseLong(v));
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private static String safeCheckDigit(String first8) {
        return first8.chars().allMatch(Character::isDigit) ? String.valueOf(AchFormat.routingCheckDigit(first8)) : "?";
    }
}
