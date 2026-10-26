package com.kadaibook.domain;

final class EnumWire {

    private EnumWire() {
    }

    static <E extends Enum<E> & WireEnum> E from(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim();
        for (E constant : type.getEnumConstants()) {
            if (constant.wire().equalsIgnoreCase(normalised) || constant.name().equalsIgnoreCase(normalised)) {
                return constant;
            }
        }
        return Enum.valueOf(type, normalised.toUpperCase(java.util.Locale.ROOT));
    }
}
