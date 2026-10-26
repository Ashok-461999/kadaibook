package com.kadaibook.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ValueStatus implements WireEnum {
    READABLE("readable"),
    AMBIGUOUS("ambiguous"),
    UNREADABLE("unreadable");

    private final String wire;

    ValueStatus(String wire) {
        this.wire = wire;
    }

    @Override
    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static ValueStatus from(String value) {
        return EnumWire.from(ValueStatus.class, value);
    }
}
