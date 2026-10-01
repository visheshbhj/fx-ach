package com.fx.ach.bai2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Meanings of BAI2 record codes, type codes and other coded fields. */
public final class Bai2Codes {

    private Bai2Codes() {
    }

    public static final Map<String, String> RECORD_TYPE = ordered(
            "01", "File header",
            "02", "Group header",
            "03", "Account identifier",
            "16", "Transaction detail",
            "88", "Continuation",
            "49", "Account trailer",
            "98", "Group trailer",
            "99", "File trailer");

    public static final Map<String, String> GROUP_STATUS = ordered(
            "1", "Update",
            "2", "Deletion",
            "3", "Correction",
            "4", "Test only");

    public static final Map<String, String> AS_OF_MODIFIER = ordered(
            "1", "Interim previous-day data",
            "2", "Final previous-day data",
            "3", "Interim same-day data",
            "4", "Final same-day data");

    public static final Map<String, String> FUNDS_TYPE = ordered(
            "", "Unknown availability (default)",
            "Z", "Unknown availability (default)",
            "0", "Immediate availability",
            "1", "One-day availability",
            "2", "Two-or-more-day availability",
            "S", "Distributed availability (immediate / one-day / two+ days)",
            "V", "Value dated",
            "D", "Distributed availability (by number of days)");

    /** Which side of the ledger a type code falls on, by its range. */
    public enum Kind {
        BALANCE("Balance"), CREDIT("Credit"), DEBIT("Debit"), LOAN("Loan"), CUSTOM("Bank-specific"), UNKNOWN("Unknown");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public static Kind kind(String typeCode) {
        String s = typeCode == null ? "" : typeCode.strip();
        if (!s.matches("\\d{3}")) {
            return Kind.UNKNOWN;
        }
        int code = Integer.parseInt(s);
        if (code >= 1 && code <= 99) {
            return Kind.BALANCE;
        }
        if (code >= 100 && code <= 399) {
            return Kind.CREDIT;
        }
        if (code >= 400 && code <= 699) {
            return Kind.DEBIT;
        }
        if (code >= 700 && code <= 799) {
            return Kind.LOAN;
        }
        if (code >= 900) {
            return Kind.CUSTOM;
        }
        return Kind.UNKNOWN;
    }

    /** Name of a type code, e.g. "142" → "ACH Credit Received"; falls back to its range. */
    public static String typeName(String typeCode) {
        String s = typeCode == null ? "" : typeCode.strip();
        String name = TYPE.get(s);
        if (name != null) {
            return name;
        }
        return switch (kind(s)) {
            case BALANCE -> "Balance / account status";
            case CREDIT -> "Credit";
            case DEBIT -> "Debit";
            case LOAN -> "Loan";
            case CUSTOM -> "Bank-specific code (see your bank's BAI2 guide)";
            case UNKNOWN -> s.isEmpty() ? "(no type code)" : "Unknown type code";
        };
    }

    public static String describe(Map<String, String> codes, String value) {
        String v = value == null ? "" : value.strip();
        String meaning = codes.get(v);
        return meaning != null ? meaning : v.isEmpty() ? "(not given)" : "Unknown code \"" + v + "\"";
    }

    /** Standard BAI2 type codes (code|name), from the BAI Cash Management Balance Reporting Specifications. */
    private static final Map<String, String> TYPE = parse("""
            010|Opening Ledger
            011|Average Opening Ledger MTD
            012|Average Opening Ledger YTD
            015|Closing Ledger
            020|Average Closing Ledger MTD
            021|Average Closing Ledger – Previous Month
            022|Aggregate Balance Adjustments
            024|Average Closing Ledger YTD – Previous Month
            025|Average Closing Ledger YTD
            030|Current Ledger
            037|ACH Net Position
            039|Opening Available + Total Same-Day ACH DTC Deposit
            040|Opening Available
            041|Average Opening Available MTD
            042|Average Opening Available YTD
            043|Average Available – Previous Month
            044|Disbursing Opening Available Balance
            045|Closing Available
            050|Average Closing Available MTD
            051|Average Closing Available – Last Month
            054|Average Closing Available YTD – Last Month
            055|Average Closing Available YTD
            056|Loan Balance
            057|Total Investment Position
            059|Current Available (CRS Suppressed)
            060|Current Available
            061|Average Current Available MTD
            062|Average Current Available YTD
            063|Total Float
            065|Target Balance
            072|One-Day Float
            073|Two or More Days Float
            074|Three or More Days Float
            075|Adjustment to Balances
            100|Total Credits
            101|Total Credit Amount MTD
            105|Credits Not Detailed
            106|Deposits Subject to Float
            107|Total Adjustment Credits YTD
            108|Credit (Any Type)
            110|Total Lockbox Deposits
            115|Lockbox Deposit
            116|Item in Lockbox Deposit
            118|Lockbox Adjustment Credit
            120|EDI Transaction Credit
            121|EDI Transaction Credit
            122|EDIBANX Credit Received
            123|EDIBANX Credit Return
            130|Total Concentration Credits
            131|DTC Concentration Credit
            135|DTC Concentration Credit
            136|Item in DTC Deposit
            140|Total ACH Credits
            142|ACH Credit Received
            143|Item in ACH Deposit
            145|ACH Concentration Credit
            146|Total Bank Card Deposits
            147|Individual Bank Card Deposit
            150|Total Preauthorized Payment Credits
            155|Preauthorized Draft Credit
            156|Item in PAC Deposit
            160|Total ACH Disbursing Funding Credits
            162|Corporate Trade Payment Settlement
            163|Corporate Trade Payment Credits
            164|Corporate Trade Payment Credit
            165|Preauthorized ACH Credit
            166|ACH Settlement
            167|ACH Settlement Credits
            168|ACH Return Item or Adjustment Settlement
            169|Miscellaneous ACH Credit
            170|Total Other Check Deposits
            171|Individual Loan Deposit
            172|Deposit Correction
            173|Bank-Prepared Deposit
            174|Other Deposit
            175|Check Deposit Package
            176|Re-presented Check Deposit
            178|List Post Credits
            180|Total Loan Proceeds
            182|Total Bank-Prepared Deposits
            184|Draft Deposit
            185|Total Miscellaneous Deposits
            186|Cash Letter Credit
            187|Cash Letter Adjustment
            190|Total Incoming Money Transfers
            191|Individual Incoming Internal Money Transfer
            195|Incoming Money Transfer
            196|Money Transfer Adjustment
            198|Compensation
            200|Total Automatic Transfer Credits
            201|Individual Automatic Transfer Credit
            202|Bond Operations Credit
            205|Total Book Transfer Credits
            206|Book Transfer Credit
            207|Total International Money Transfer Credits
            208|Individual International Money Transfer Credit
            210|Total International Credits
            212|Foreign Letter of Credit
            213|Letter of Credit
            214|Foreign Exchange of Credit
            215|Total Letters of Credit
            216|Foreign Remittance Credit
            218|Foreign Collection Credit
            221|Foreign Check Purchase
            222|Foreign Checks Deposited
            224|Commission
            226|International Money Market Trading
            227|Standing Order
            229|Miscellaneous International Credit
            230|Total Security Credits
            231|Total Collection Credits
            232|Sale of Debt Security
            233|Securities Sold
            234|Sale of Equity Security
            235|Matured Reverse Repurchase Order
            236|Maturity of Debt Security
            237|Individual Collection Credit
            238|Collection of Dividends
            239|Total Bankers' Acceptance Credits
            240|Coupon Collections – Banks
            241|Bankers' Acceptances
            242|Collection of Interest Income
            243|Matured Fed Funds Purchased
            244|Interest/Matured Principal Payment
            245|Monthly Dividends
            246|Commercial Paper
            247|Capital Change
            248|Savings Bonds Sales Adjustment
            249|Miscellaneous Security Credit
            250|Total Checks Posted and Returned
            251|Total Debit Reversals
            252|Debit Reversal
            254|Posting Error Correction Credit
            255|Check Posted and Returned
            256|Total ACH Return Items
            257|Individual ACH Return Item
            258|ACH Reversal Credit
            260|Total Rejected Credits
            261|Individual Rejected Credit
            263|Overdraft
            266|Return Item
            268|Return Item Adjustment
            270|Total ZBA Credits
            271|Net Zero-Balance Amount
            274|Cumulative ZBA or Disbursement Credits
            275|ZBA Credit
            276|ZBA Float Adjustment
            277|ZBA Credit Transfer
            278|ZBA Credit Adjustment
            280|Total Controlled Disbursing Credits
            281|Individual Controlled Disbursing Credit
            285|Total DTC Disbursing Credits
            286|Individual DTC Disbursing Credit
            294|Total ATM Credits
            295|ATM Credit
            301|Commercial Deposit
            302|Correspondent Bank Deposit
            303|Total Wire Transfers In – FF
            304|Total Wire Transfers In – CHF
            305|Total Fed Funds Sold
            306|Fed Funds Sold
            307|Total Trust Credits
            308|Trust Credit
            309|Total Value-Dated Funds
            310|Total Commercial Deposits
            315|Total International Credits – FF
            316|Total International Credits – CHF
            318|Total Foreign Check Purchased
            319|Late Deposit
            320|Total Securities Sold – FF
            321|Total Securities Sold – CHF
            324|Total Securities Matured – FF
            325|Total Securities Matured – CHF
            326|Securities Interest
            327|Securities Matured
            328|Securities Interest – FF
            329|Securities Interest – CHF
            330|Total Escrow Credits
            331|Individual Escrow Credit
            332|Miscellaneous Securities Credits – FF
            336|Miscellaneous Securities Credits – CHF
            338|Total Securities Sold
            340|Total Broker Deposits
            341|Total Broker Deposits – FF
            342|Broker Deposit
            343|Total Broker Deposits – CHF
            344|Individual Back Value Credit
            345|Item in Brokers Deposit
            346|Sweep Interest Income
            347|Sweep Principal Sell
            348|Futures Credit
            349|Principal Payments Credit
            350|Investment Sold
            351|Individual Investment Sold
            352|Total Cash Center Credits
            353|Cash Center Credit
            354|Interest Credit
            355|Investment Interest
            356|Total Credit Adjustment
            357|Credit Adjustment
            358|YTD Adjustment Credit
            359|Interest Adjustment Credit
            360|Total Credits Less Wire Transfer and Returned Checks
            361|Grand Total Credits Less Grand Total Debits
            362|Correspondent Collection
            363|Correspondent Collection Adjustment
            364|Loan Participation
            366|Currency and Coin Deposited
            367|Food Stamp Letter
            368|Food Stamp Adjustment
            369|Clearing Settlement Credit
            370|Total Back Value Credits
            372|Back Value Adjustment
            373|Customer Payroll
            374|FRB Statement Recap
            376|Savings Bond Letter or Adjustment
            377|Treasury Tax and Loan Credit
            378|Transfer of Treasury Credit
            379|FRB Government Checks Cash Letter Credit
            381|FRB Government Check Adjustment
            382|FRB Postal Money Order Credit
            383|FRB Postal Money Order Adjustment
            384|FRB Cash Letter Auto Charge Credit
            385|Total Universal Credits
            386|FRB Cash Letter Auto Charge Adjustment
            387|FRB Fine-Sort Cash Letter Credit
            388|FRB Fine-Sort Adjustment
            389|Total Freight Payment Credits
            390|Total Miscellaneous Credits
            391|Universal Credit
            392|Freight Payment Credit
            393|Itemized Credit Over $10,000
            394|Cumulative Credits
            395|Check Reversal
            397|Float Adjustment
            398|Miscellaneous Fee Refund
            399|Miscellaneous Credit
            400|Total Debits
            401|Total Debit Amount MTD
            403|Today's Total Debits
            405|Total Debit Less Wire Transfers and Charge-Backs
            406|Debits Not Detailed
            408|Float Adjustment
            409|Debit (Any Type)
            410|Total YTD Adjustment
            412|Total Debits (Excluding Returned Items)
            415|Lockbox Debit
            416|Total Lockbox Debits
            420|EDI Transaction Debits
            421|EDI Transaction Debit
            422|EDIBANX Settlement Debit
            423|EDIBANX Return Item Debit
            430|Total Payable-Through Drafts
            435|Payable-Through Draft
            445|ACH Concentration Debit
            446|Total ACH Disbursement Funding Debits
            447|ACH Disbursement Funding Debit
            450|Total ACH Debits
            451|ACH Debit Received
            452|Item in ACH Disbursement or Debit
            455|Preauthorized ACH Debit
            462|Account Holder Initiated ACH Debit
            463|Corporate Trade Payment Debits
            464|Corporate Trade Payment Debit
            465|Corporate Trade Payment Settlement
            466|ACH Settlement
            467|ACH Settlement Debits
            468|ACH Return Item or Adjustment Settlement
            469|Miscellaneous ACH Debit
            470|Total Check Paid
            471|Total Check Paid – Cumulative MTD
            472|Cumulative Checks Paid
            474|Certified Check Debit
            475|Check Paid
            476|Federal Reserve Bank Letter Debit
            477|Bank Originated Debit
            478|List Post Debits
            479|List Post Debit
            480|Total Loan Payments
            481|Individual Loan Payment
            482|Total Bank-Originated Debits
            484|Draft
            485|DTC Debit
            486|Total Cash Letter Debits
            487|Cash Letter Debit
            489|Cash Letter Adjustment
            490|Total Outgoing Money Transfers
            491|Individual Outgoing Internal Money Transfer
            493|Customer Terminal Initiated Money Transfer
            495|Outgoing Money Transfer
            496|Money Transfer Adjustment
            498|Compensation
            500|Total Automatic Transfer Debits
            501|Individual Automatic Transfer Debit
            502|Bond Operations Debit
            505|Total Book Transfer Debits
            506|Book Transfer Debit
            507|Total International Money Transfer Debits
            508|Individual International Money Transfer Debit
            510|Total International Debits
            512|Letter of Credit Debit
            513|Letter of Credit
            514|Foreign Exchange Debit
            515|Total Letters of Credit
            516|Foreign Remittance Debit
            518|Foreign Collection Debit
            522|Foreign Checks Paid
            524|Commission
            526|International Money Market Trading
            527|Standing Order
            529|Miscellaneous International Debit
            530|Total Security Debits
            531|Securities Purchased
            532|Total Amount of Securities Purchased
            533|Security Collection Debit
            535|Purchase of Equity Securities
            537|Total Collection Debit
            538|Matured Repurchase Order
            539|Total Bankers' Acceptances Debit
            540|Coupon Collection Debit
            541|Bankers' Acceptances
            542|Purchase of Debt Securities
            543|Domestic Collection
            544|Interest/Matured Principal Payment
            546|Commercial Paper
            547|Capital Change
            548|Savings Bonds Sales Adjustment
            549|Miscellaneous Security Debit
            550|Total Deposited Items Returned
            551|Total Credit Reversals
            552|Credit Reversal
            554|Posting Error Correction Debit
            555|Deposited Item Returned
            556|Total ACH Return Items
            557|Individual ACH Return Item
            558|ACH Reversal Debit
            560|Total Rejected Debits
            561|Individual Rejected Debit
            563|Overdraft
            564|Overdraft Fee
            566|Return Item
            567|Return Item Fee
            568|Return Item Adjustment
            570|Total ZBA Debits
            574|Cumulative ZBA Debits
            575|ZBA Debit
            577|ZBA Debit Transfer
            578|ZBA Debit Adjustment
            580|Total Controlled Disbursing Debits
            581|Individual Controlled Disbursing Debit
            583|Total Disbursing Checks Paid – Early Amount
            584|Total Disbursing Checks Paid – Later Amount
            585|Disbursing Funding Requirement
            586|FRB Presentment Estimate (Fed Estimate)
            587|Late Debits (After Notification)
            588|Total Disbursing Checks Paid – Last Amount
            590|Total DTC Debits
            594|Total ATM Debits
            595|ATM Debit
            596|Total ARP Debits
            597|ARP Debit
            601|Estimated Total Disbursement
            602|Adjusted Total Disbursement
            610|Total Funds Required
            611|Total Wire Transfers Out – CHF
            612|Total Wire Transfers Out – FF
            613|Total International Debit – CHF
            614|Total International Debit – FF
            615|Total Federal Reserve Bank – Commercial Bank Debit
            616|Federal Reserve Bank – Commercial Bank Debit
            617|Total Securities Purchased – CHF
            618|Total Securities Purchased – FF
            621|Total Broker Debits – CHF
            622|Broker Debit
            623|Total Broker Debits – FF
            625|Total Broker Debits
            626|Fed Funds Purchased
            627|Total Cash Center Debits
            628|Cash Center Debit
            629|Total Debit Adjustments
            630|Debit Adjustment
            631|Total Trust Debits
            632|Trust Debit
            633|YTD Adjustment Debit
            640|Total Escrow Debits
            641|Individual Escrow Debit
            644|Individual Back Value Debit
            646|Transfer Calculation Debit
            650|Investments Purchased
            651|Individual Investment Purchase
            654|Interest Debit
            655|Total Investment Interest Debits
            656|Sweep Principal Buy
            657|Futures Debit
            658|Principal Payments Debit
            659|Interest Adjustment Debit
            661|Account Analysis Fee
            662|Correspondent Collection Debit
            663|Correspondent Collection Adjustment
            664|Loan Participation
            665|Intercept Debit
            666|Currency and Coin Shipped
            667|Food Stamp Letter
            668|Food Stamp Adjustment
            669|Clearing Settlement Debit
            670|Total Back Value Debits
            672|Back Value Adjustment
            673|Customer Payroll
            674|FRB Statement Recap
            676|Savings Bond Letter or Adjustment
            677|Treasury Tax and Loan Debit
            678|Transfer of Treasury Debit
            679|FRB Government Checks Cash Letter Debit
            681|FRB Government Check Adjustment
            682|FRB Postal Money Order Debit
            683|FRB Postal Money Order Adjustment
            684|FRB Cash Letter Auto Charge Debit
            685|Total Universal Debits
            686|FRB Cash Letter Auto Charge Adjustment
            687|FRB Fine-Sort Cash Letter Debit
            688|FRB Fine-Sort Adjustment
            689|FRB Freight Payment Debits
            690|Total Miscellaneous Debits
            691|Universal Debit
            692|Freight Payment Debit
            693|Itemized Debit Over $10,000
            694|Deposit Reversal
            695|Deposit Correction Debit
            696|Regular Collection Debit
            697|Cumulative Debits
            698|Miscellaneous Fees
            699|Miscellaneous Debit
            701|Principal Loan Balance
            703|Available Commitment Amount
            705|Payment Amount Due
            707|Principal Amount Past Due
            709|Interest Amount Past Due
            720|Total Loan Payment
            721|Amount Applied to Interest
            722|Amount Applied to Principal
            723|Amount Applied to Escrow
            724|Amount Applied to Late Charges
            725|Amount Applied to Buydown
            726|Amount Applied to Misc. Fees
            727|Amount Applied to Deferred Interest Detail
            728|Amount Applied to Service Charge
            760|Loan Disbursement
            """);

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, String> parse(String table) {
        Map<String, String> m = new LinkedHashMap<>();
        table.lines().filter(l -> !l.isBlank()).forEach(l -> {
            int bar = l.indexOf('|');
            m.put(l.substring(0, bar).strip(), l.substring(bar + 1).strip());
        });
        return Collections.unmodifiableMap(m);
    }
}
