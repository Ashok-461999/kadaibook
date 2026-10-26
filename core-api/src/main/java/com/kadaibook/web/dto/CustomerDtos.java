package com.kadaibook.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CreateCustomerRequest(
            @NotBlank @Size(max = 200) String displayName,
            @Size(max = 20) List<@Size(max = 200) String> aliases
    ) {
    }

    public record CustomerDto(
            UUID id,
            String displayName,
            List<String> aliases,
            Instant createdAt,
            long balancePaise
    ) {
    }

    public record LedgerRowDto(
            UUID id,
            LocalDate date,
            String description,
            long debitPaise,
            long creditPaise,
            UUID sourcePageId,
            Instant createdAt
    ) {
    }

    public record CustomerLedgerDto(
            UUID customerId,
            String displayName,
            long balancePaise,
            List<LedgerRowDto> entries
    ) {
    }
}
