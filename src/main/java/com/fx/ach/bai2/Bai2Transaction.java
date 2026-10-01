package com.fx.ach.bai2;

/** A transaction detail (16) record, continuations included. */
public record Bai2Transaction(Bai2Record record, String typeCode, String amountRaw, Bai2Funds funds,
                              String bankReference, String customerReference, String text) {

    /** Amount in minor units (cents for USD), or null if blank or not a number. */
    public Long amount() {
        return Bai2Format.parseAmount(amountRaw);
    }

    public Bai2Codes.Kind kind() {
        return Bai2Codes.kind(typeCode);
    }
}
