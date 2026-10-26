package com.kadaibook.ai;

public class AiServiceException extends RuntimeException {

    public enum Kind {
        TRANSPORT,
        AUTH,
        REJECTED,
        INVALID_RESPONSE
    }

    private final Kind kind;

    public AiServiceException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
