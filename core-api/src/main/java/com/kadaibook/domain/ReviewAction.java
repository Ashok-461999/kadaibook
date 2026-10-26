package com.kadaibook.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReviewAction implements WireEnum {
    CONFIRMED("confirmed"),
    CORRECTED("corrected"),
    REJECTED("rejected");

    private final String wire;

    ReviewAction(String wire) {
        this.wire = wire;
    }

    @Override
    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static ReviewAction from(String value) {
        return EnumWire.from(ReviewAction.class, value);
    }
}
