package com.kadaibook.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ShopDtos {

    private ShopDtos() {
    }

    public record CreateShopRequest(
            @NotBlank @Size(max = 160) String name,
            @AssertTrue(message = "written consent is required before collecting notebook pages") boolean consent
    ) {
    }

    public record ShopDto(UUID id, String name, Instant consentAt, Instant createdAt) {
    }
}
