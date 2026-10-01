package com.fx.ach.bai2;

/**
 * One balance or activity summary on an account identifier (03) record: a type code with its
 * amount, optional item count and funds availability.
 */
public record Bai2Summary(String typeCode, String amountRaw, String itemCount, Bai2Funds funds) {

    /** Amount in minor units (cents for USD), or null if blank or not a number. */
    public Long amount() {
        return Bai2Format.parseAmount(amountRaw);
    }
}
