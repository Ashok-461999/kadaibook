package com.kadaibook.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PageStatus implements WireEnum {
    UPLOADED("uploaded"),
    EXTRACTING("extracting"),
    NEEDS_REVIEW("needs_review"),
    EXTRACTION_FAILED("extraction_failed"),
    IMPORTED("imported");

    private final String wire;

    PageStatus(String wire) {
        this.wire = wire;
    }

    @Override
    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static PageStatus from(String value) {
        return EnumWire.from(PageStatus.class, value);
    }
}
