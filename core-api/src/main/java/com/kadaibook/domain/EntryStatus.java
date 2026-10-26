package com.kadaibook.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EntryStatus implements WireEnum {
    ACTIVE("active"),
    CROSSED_OUT("crossed_out"),
    UNCERTAIN("uncertain"),
    CORRECTED("corrected"),
    REJECTED("rejected");

    private final String wire;

    EntryStatus(String wire) {
        this.wire = wire;
    }

    public boolean importable() {
        return this == ACTIVE || this == UNCERTAIN || this == CORRECTED;
    }

    @Override
    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static EntryStatus from(String value) {
        return EnumWire.from(EntryStatus.class, value);
    }
}
