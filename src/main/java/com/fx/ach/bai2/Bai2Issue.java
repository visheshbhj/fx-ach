package com.fx.ach.bai2;

/** A structural or business-rule problem found in a BAI2 file. {@code record} may be null. */
public record Bai2Issue(Severity severity, String message, Bai2Record record) {

    public enum Severity { ERROR, WARNING }

    @Override
    public String toString() {
        return (record != null ? "Line " + record.lineNumber() + ": " : "") + message;
    }
}
