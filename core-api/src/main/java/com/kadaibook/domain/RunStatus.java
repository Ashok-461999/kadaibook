package com.kadaibook.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RunStatus implements WireEnum {
    RUNNING("running"),
    SUCCESS("success"),
    EXTRACTION_FAILED("extraction_failed"),
    UNSUPPORTED_LAYOUT("unsupported_layout"),
    TRANSPORT_ERROR("transport_error");

    private final String wire;

    RunStatus(String wire) {
        this.wire = wire;
    }

    @Override
    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static RunStatus from(String value) {
        return EnumWire.from(RunStatus.class, value);
    }
}
