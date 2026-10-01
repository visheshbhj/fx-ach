package com.fx.ach.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-English dictionaries for the coded values found in NACHA files.
 */
public final class AchCodes {

    public static final Map<String, String> SERVICE_CLASS = new LinkedHashMap<>();
    public static final Map<String, String> SEC = new LinkedHashMap<>();
    public static final Map<String, String> TRANSACTION = new LinkedHashMap<>();
    public static final Map<String, String> ADDENDA_TYPE = new LinkedHashMap<>();
    public static final Map<String, String> RETURN_REASON = new LinkedHashMap<>();
    public static final Map<String, String> CHANGE_CODE = new LinkedHashMap<>();
    public static final Map<String, String> RECORD_TYPE = new LinkedHashMap<>();

    static {
        RECORD_TYPE.put("1", "File Header");
        RECORD_TYPE.put("5", "Batch Header");
        RECORD_TYPE.put("6", "Entry Detail");
        RECORD_TYPE.put("7", "Addenda");
        RECORD_TYPE.put("8", "Batch Control");
        RECORD_TYPE.put("9", "File Control");

        SERVICE_CLASS.put("200", "Mixed debits and credits");
        SERVICE_CLASS.put("220", "Credits only");
        SERVICE_CLASS.put("225", "Debits only");
        SERVICE_CLASS.put("280", "Automated accounting advices");

        SEC.put("PPD", "Prearranged Payment & Deposit (consumer, e.g. payroll, bill pay)");
        SEC.put("CCD", "Corporate Credit or Debit (business to business)");
        SEC.put("CTX", "Corporate Trade Exchange (B2B with EDI remittance)");
        SEC.put("WEB", "Internet-initiated / mobile entry (consumer)");
        SEC.put("TEL", "Telephone-initiated entry (consumer)");
        SEC.put("ARC", "Accounts Receivable check conversion");
        SEC.put("BOC", "Back Office check conversion");
        SEC.put("POP", "Point-of-Purchase check conversion");
        SEC.put("POS", "Point-of-Sale entry");
        SEC.put("RCK", "Re-presented Check entry");
        SEC.put("CIE", "Customer-Initiated Entry");
        SEC.put("COR", "Notification of Change / refused NOC");
        SEC.put("DNE", "Death Notification Entry");
        SEC.put("XCK", "Destroyed Check entry");
        SEC.put("IAT", "International ACH Transaction");

        TRANSACTION.put("21", "Checking · return/NOC credit");
        TRANSACTION.put("22", "Checking · credit (deposit)");
        TRANSACTION.put("23", "Checking · prenote credit");
        TRANSACTION.put("24", "Checking · zero-dollar credit with remittance");
        TRANSACTION.put("26", "Checking · return/NOC debit");
        TRANSACTION.put("27", "Checking · debit (withdrawal)");
        TRANSACTION.put("28", "Checking · prenote debit");
        TRANSACTION.put("29", "Checking · zero-dollar debit with remittance");
        TRANSACTION.put("31", "Savings · return/NOC credit");
        TRANSACTION.put("32", "Savings · credit (deposit)");
        TRANSACTION.put("33", "Savings · prenote credit");
        TRANSACTION.put("34", "Savings · zero-dollar credit with remittance");
        TRANSACTION.put("36", "Savings · return/NOC debit");
        TRANSACTION.put("37", "Savings · debit (withdrawal)");
        TRANSACTION.put("38", "Savings · prenote debit");
        TRANSACTION.put("39", "Savings · zero-dollar debit with remittance");
        TRANSACTION.put("41", "General ledger · return/NOC credit");
        TRANSACTION.put("42", "General ledger · credit");
        TRANSACTION.put("43", "General ledger · prenote credit");
        TRANSACTION.put("44", "General ledger · zero-dollar credit with remittance");
        TRANSACTION.put("46", "General ledger · return/NOC debit");
        TRANSACTION.put("47", "General ledger · debit");
        TRANSACTION.put("48", "General ledger · prenote debit");
        TRANSACTION.put("49", "General ledger · zero-dollar debit with remittance");
        TRANSACTION.put("51", "Loan · return/NOC credit");
        TRANSACTION.put("52", "Loan · credit (payment)");
        TRANSACTION.put("53", "Loan · prenote credit");
        TRANSACTION.put("54", "Loan · zero-dollar credit with remittance");
        TRANSACTION.put("55", "Loan · debit (reversal)");
        TRANSACTION.put("56", "Loan · return/NOC debit");

        ADDENDA_TYPE.put("02", "Point-of-sale terminal information");
        ADDENDA_TYPE.put("05", "Payment-related information (remittance)");
        ADDENDA_TYPE.put("10", "IAT addenda 1 – transaction & receiver");
        ADDENDA_TYPE.put("11", "IAT addenda 2 – originator name & address");
        ADDENDA_TYPE.put("12", "IAT addenda 3 – originator city/country");
        ADDENDA_TYPE.put("13", "IAT addenda 4 – originating DFI");
        ADDENDA_TYPE.put("14", "IAT addenda 5 – receiving DFI");
        ADDENDA_TYPE.put("15", "IAT addenda 6 – receiver ID & address");
        ADDENDA_TYPE.put("16", "IAT addenda 7 – receiver city/country");
        ADDENDA_TYPE.put("17", "IAT remittance information");
        ADDENDA_TYPE.put("18", "IAT foreign correspondent bank");
        ADDENDA_TYPE.put("98", "Notification of Change (NOC)");
        ADDENDA_TYPE.put("99", "Return");

        RETURN_REASON.put("R01", "Insufficient funds");
        RETURN_REASON.put("R02", "Account closed");
        RETURN_REASON.put("R03", "No account / unable to locate account");
        RETURN_REASON.put("R04", "Invalid account number structure");
        RETURN_REASON.put("R05", "Unauthorized debit to consumer account using corporate SEC code");
        RETURN_REASON.put("R06", "Returned per ODFI's request");
        RETURN_REASON.put("R07", "Authorization revoked by customer");
        RETURN_REASON.put("R08", "Payment stopped");
        RETURN_REASON.put("R09", "Uncollected funds");
        RETURN_REASON.put("R10", "Customer advises unauthorized, improper or ineligible");
        RETURN_REASON.put("R11", "Customer advises entry not in accordance with authorization terms");
        RETURN_REASON.put("R12", "Account sold to another DFI");
        RETURN_REASON.put("R13", "Invalid ACH routing number");
        RETURN_REASON.put("R14", "Representative payee deceased");
        RETURN_REASON.put("R15", "Beneficiary or account holder deceased");
        RETURN_REASON.put("R16", "Account frozen / returned per OFAC");
        RETURN_REASON.put("R17", "File record edit criteria / questionable entry");
        RETURN_REASON.put("R18", "Improper effective entry date");
        RETURN_REASON.put("R19", "Amount field error");
        RETURN_REASON.put("R20", "Non-transaction account");
        RETURN_REASON.put("R21", "Invalid company identification");
        RETURN_REASON.put("R22", "Invalid individual ID number");
        RETURN_REASON.put("R23", "Credit entry refused by receiver");
        RETURN_REASON.put("R24", "Duplicate entry");
        RETURN_REASON.put("R25", "Addenda error");
        RETURN_REASON.put("R26", "Mandatory field error");
        RETURN_REASON.put("R27", "Trace number error");
        RETURN_REASON.put("R28", "Routing number check digit error");
        RETURN_REASON.put("R29", "Corporate customer advises not authorized");
        RETURN_REASON.put("R30", "RDFI not participant in check truncation program");
        RETURN_REASON.put("R31", "Permissible return entry (CCD and CTX only)");
        RETURN_REASON.put("R32", "RDFI non-settlement");
        RETURN_REASON.put("R33", "Return of XCK entry");
        RETURN_REASON.put("R34", "Limited participation DFI");
        RETURN_REASON.put("R35", "Return of improper debit entry");
        RETURN_REASON.put("R36", "Return of improper credit entry");
        RETURN_REASON.put("R37", "Source document presented for payment");
        RETURN_REASON.put("R38", "Stop payment on source document");
        RETURN_REASON.put("R39", "Improper source document / source document presented");
        RETURN_REASON.put("R50", "State law affecting RCK acceptance");
        RETURN_REASON.put("R51", "Item related to RCK entry is ineligible or improper");
        RETURN_REASON.put("R52", "Stop payment on item related to RCK entry");
        RETURN_REASON.put("R53", "Item and RCK entry presented for payment");
        RETURN_REASON.put("R61", "Misrouted return");
        RETURN_REASON.put("R62", "Return of erroneous or reversing debit");
        RETURN_REASON.put("R67", "Duplicate return");
        RETURN_REASON.put("R68", "Untimely return");
        RETURN_REASON.put("R69", "Field error(s)");
        RETURN_REASON.put("R70", "Permissible return entry not accepted / return not requested by ODFI");
        RETURN_REASON.put("R71", "Misrouted dishonored return");
        RETURN_REASON.put("R72", "Untimely dishonored return");
        RETURN_REASON.put("R73", "Timely original return");
        RETURN_REASON.put("R74", "Corrected return");
        RETURN_REASON.put("R75", "Return not a duplicate");
        RETURN_REASON.put("R76", "No errors found");
        RETURN_REASON.put("R77", "Non-acceptance of R62 dishonored return");
        RETURN_REASON.put("R80", "IAT entry coding error");
        RETURN_REASON.put("R81", "Non-participant in IAT program");
        RETURN_REASON.put("R82", "Invalid foreign receiving DFI identification");
        RETURN_REASON.put("R83", "Foreign receiving DFI unable to settle");
        RETURN_REASON.put("R84", "Entry not processed by gateway");
        RETURN_REASON.put("R85", "Incorrectly coded outbound international payment");

        CHANGE_CODE.put("C01", "Incorrect DFI account number");
        CHANGE_CODE.put("C02", "Incorrect routing number");
        CHANGE_CODE.put("C03", "Incorrect routing number and account number");
        CHANGE_CODE.put("C04", "Incorrect individual name / receiving company name");
        CHANGE_CODE.put("C05", "Incorrect transaction code");
        CHANGE_CODE.put("C06", "Incorrect account number and transaction code");
        CHANGE_CODE.put("C07", "Incorrect routing number, account number and transaction code");
        CHANGE_CODE.put("C08", "Incorrect receiving DFI identification (IAT only)");
        CHANGE_CODE.put("C09", "Incorrect individual identification number");
        CHANGE_CODE.put("C13", "Addenda format error");
        CHANGE_CODE.put("C14", "Incorrect SEC code for outbound international payment");
        CHANGE_CODE.put("C61", "Misrouted notification of change");
        CHANGE_CODE.put("C62", "Incorrect trace number");
        CHANGE_CODE.put("C63", "Incorrect company identification number");
        CHANGE_CODE.put("C64", "Incorrect individual identification number / identification number");
        CHANGE_CODE.put("C65", "Incorrectly formatted corrected data");
        CHANGE_CODE.put("C66", "Incorrect discretionary data");
        CHANGE_CODE.put("C67", "Routing number not from original entry detail record");
        CHANGE_CODE.put("C68", "DFI account number not from original entry detail record");
        CHANGE_CODE.put("C69", "Incorrect transaction code");
    }

    private AchCodes() {
    }

    public static String describe(Map<String, String> table, String code) {
        if (code == null) {
            return "";
        }
        String key = code.trim();
        return table.getOrDefault(key, key.isEmpty() ? "" : "Unknown code " + key);
    }

    /** True when the transaction code moves money into the receiver's account. */
    public static boolean isCredit(int transactionCode) {
        int kind = transactionCode % 10;
        return kind >= 1 && kind <= 4;
    }

    public static boolean isDebit(int transactionCode) {
        int kind = transactionCode % 10;
        return kind >= 5 && kind <= 9;
    }

    public static boolean isPrenote(int transactionCode) {
        int kind = transactionCode % 10;
        return kind == 3 || kind == 8;
    }

    public static String accountType(int transactionCode) {
        return switch (transactionCode / 10) {
            case 2 -> "Checking";
            case 3 -> "Savings";
            case 4 -> "General ledger";
            case 5 -> "Loan";
            default -> "Unknown";
        };
    }
}
