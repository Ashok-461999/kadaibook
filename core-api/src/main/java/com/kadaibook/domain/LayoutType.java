package com.kadaibook.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LayoutType implements WireEnum {
    CUSTOMER_LEDGER("customer_ledger"),
    DATE_WISE("date_wise"),
    SUMMARY("summary"),
    UNKNOWN("unknown");

    private final String wire;

    LayoutType(String wire) {
        this.wire = wire;
    }

    @Override
    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static LayoutType from(String value) {
        return EnumWire.from(LayoutType.class, value);
    }
}
