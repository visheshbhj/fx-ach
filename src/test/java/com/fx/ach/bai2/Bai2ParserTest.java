package com.fx.ach.bai2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bai2ParserTest {

    private static final String SMALL = """
            01,BANK,CUST,261001,0600,7,,,2/
            02,CUST,BANK,1,260930,,USD,2/
            03,111,,010,1000,,,015,1500,,/
            16,142,500,Z,REF1,,ACH CREDIT/
            49,3000,3/
            98,3000,1,5/
            99,3000,1,7/
            """;

    private static String sample() throws IOException {
        return Files.readString(Path.of("samples/bai2_prior_day.txt"), StandardCharsets.ISO_8859_1);
    }

    private static List<Bai2Issue> errors(Bai2File f) {
        return Bai2Validator.validate(f).stream().filter(i -> i.severity() == Bai2Issue.Severity.ERROR).toList();
    }

    @Test
    void detectsBai2ByContentNotExtension() throws IOException {
        assertTrue(Bai2Parser.looksLikeBai2(sample()));
        assertTrue(Bai2Parser.looksLikeBai2("\n\n  01,A,B,261001,0600,1,,,2/\n"));
        assertTrue(Bai2Parser.looksLikeBai2("﻿01,A,B,261001,0600,1,,,2/"));
        assertFalse(Bai2Parser.looksLikeBai2(Files.readString(Path.of("samples/payroll.ach"))));
        assertFalse(Bai2Parser.looksLikeBai2(""));
    }

    @Test
    void sampleIsValidAndStructured() throws IOException {
        Bai2File f = Bai2Parser.parse(sample());
        assertEquals(List.of(), Bai2Validator.validate(f));
        assertEquals("121000358", f.sender());
        assertEquals(1, f.groups().size());
        assertEquals(2, f.accounts().size());
        Bai2Account a = f.accounts().get(0);
        assertEquals("1234567890", a.accountNumber());
        assertEquals(6, a.summaries().size(), "88 continuation adds the 100 and 400 summaries");
        assertEquals("400", a.summaries().get(5).typeCode());
        assertEquals("3", a.summaries().get(5).itemCount());
        assertEquals(6, a.transactions().size());
        assertEquals(420_000, a.credits());
        assertEquals(203_950, a.debits());
        assertEquals(17, f.lines().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bai2_prior_day.txt", "bai2_intraday.bai", "bai2_multi_bank.dat"})
    void validSamplesHaveNoIssues(String name) throws IOException {
        String text = Files.readString(Path.of("samples", name), StandardCharsets.ISO_8859_1);
        assertTrue(Bai2Parser.looksLikeBai2(text));
        assertEquals(List.of(), Bai2Validator.validate(Bai2Parser.parse(text)));
    }

    @Test
    void multiBankSampleKeepsEachAccountsCurrency() throws IOException {
        Bai2File f = Bai2Parser.parse(Files.readString(Path.of("samples/bai2_multi_bank.dat"), StandardCharsets.ISO_8859_1));
        assertEquals(List.of("USD", "EUR", "JPY"), f.accounts().stream().map(Bai2Account::currency).toList());
        assertEquals("ACH CREDIT, ORIG: STRIPE PAYOUTS, BATCH 9921", f.accounts().get(0).transactions().get(0).text());
    }

    @Test
    void errorSampleShowsOneProblemPerCheck() throws IOException {
        Bai2File f = Bai2Parser.parse(Files.readString(Path.of("samples/bai2_with_errors.txt"), StandardCharsets.ISO_8859_1));
        List<String> messages = Bai2Validator.validate(f).stream().map(Bai2Issue::toString).toList();
        assertEquals(7, messages.size(), String.join("\n", messages));
        for (String expected : List.of("no account trailer (49)", "not a valid YYMMDD date", "is a balance code",
                "funds type \"X\"", "Account control total", "\"12O000\" is not a number", "Number of records is 99")) {
            assertTrue(messages.stream().anyMatch(m -> m.contains(expected)), expected + " in " + messages);
        }
    }

    @Test
    void transactionTextContinuesOnto88() throws IOException {
        Bai2Transaction wire = Bai2Parser.parse(sample()).accounts().get(0).transactions().get(1);
        assertEquals("FW00099812", wire.bankReference());
        assertEquals("INV-20931", wire.customerReference());
        assertEquals("INCOMING WIRE FROM GLOBEX CORP REF INV 20931 SEPT SERVICES", wire.text());
    }

    @Test
    void fundsTypesShiftTheReferenceFields() throws IOException {
        Bai2File f = Bai2Parser.parse(sample());
        Bai2Transaction s = f.accounts().get(0).transactions().get(2);
        assertEquals(List.of("10000", "5000", "5000"), s.funds().values());
        assertEquals("DEP0001", s.bankReference());
        Bai2Transaction v = f.accounts().get(0).transactions().get(5);
        assertEquals("FW00099901", v.bankReference());
        assertEquals("OUTGOING WIRE TO INITECH LLC", v.text());
        Bai2Transaction d = f.accounts().get(1).transactions().get(0);
        assertEquals(List.of("2", "1", "40000", "2", "20000"), d.funds().values());
        assertEquals("DEP0002", d.bankReference());
        assertEquals("CHECK DEPOSIT PACKAGE", d.text());
    }

    @Test
    void textMayContainCommasAndSlashes() {
        Bai2File f = Bai2Parser.parse(SMALL.replace("ACH CREDIT/", "PAYMENT, INV 12/34/"));
        assertEquals("PAYMENT, INV 12/34", f.accounts().get(0).transactions().get(0).text());
        assertEquals(List.of(), errors(f));
    }

    @Test
    void crlfPaddingAndBlankLinesAreTolerated() {
        String messy = SMALL.lines().map(l -> String.format("%-80s", l)).reduce("", (a, b) -> a + b + "\r\n") + "\r\n\r\n";
        Bai2File f = Bai2Parser.parse(messy);
        assertEquals(List.of(), Bai2Validator.validate(f));
    }

    @Test
    void fileWithoutLineBreaksIsSplitAtRecordTerminators() {
        Bai2File f = Bai2Parser.parse(SMALL.replace("\n", ""));
        assertEquals(7, f.lines().size());
        assertEquals(List.of(), Bai2Validator.validate(f));
    }

    @Test
    void wrongTotalsAndCountsAreReported() {
        Bai2File f = Bai2Parser.parse(SMALL.replace("49,3000,3/", "49,2999,4/"));
        List<Bai2Issue> errors = errors(f);
        assertTrue(errors.stream().anyMatch(i -> i.message().startsWith("Account control total")), errors.toString());
        assertTrue(errors.stream().anyMatch(i -> i.message().startsWith("Number of records for account")), errors.toString());
        assertEquals(5, errors.get(0).record().lineNumber());
    }

    @Test
    void missingTrailersAreReported() {
        Bai2File f = Bai2Parser.parse(SMALL.replace("49,3000,3/\n", "").replace("99,3000,1,7/\n", ""));
        List<String> messages = errors(f).stream().map(Bai2Issue::message).toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains("no account trailer (49)")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.contains("File trailer (99) is missing")), messages.toString());
        assertEquals(1, f.accounts().get(0).transactions().size(), "still reads what is there");
    }

    @Test
    void accountCurrencyFallsBackToGroupAndDecimalsFollowIt() {
        Bai2File f = Bai2Parser.parse(SMALL.replace(",USD,2/", ",JPY,2/"));
        assertEquals("JPY", f.accounts().get(0).currency());
        assertEquals("¥500", Bai2Format.money(500, "JPY"));
        assertEquals("$5.00", Bai2Format.money(500, "USD"));
    }

    @Test
    void segmentsCoverEveryCharacterOfEachLine() throws IOException {
        Bai2File f = Bai2Parser.parse(sample());
        for (Bai2Record r : f.records()) {
            List<List<Bai2Fields.Segment>> segs = Bai2Fields.segments(f, r);
            for (int k = 0; k < r.lines().size(); k++) {
                String joined = segs.get(k).stream().map(Bai2Fields.Segment::text).reduce("", String::concat);
                assertEquals(r.lines().get(k).text(), joined);
            }
        }
    }
}
