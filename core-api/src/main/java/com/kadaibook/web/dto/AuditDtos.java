package com.kadaibook.web.dto;

import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditEntryDto(
            UUID id,
            String action,
            String entityType,
            UUID entityId,
            String actorName,
            String beforeJson,
            String afterJson,
            Instant at
    ) {
    }
}
