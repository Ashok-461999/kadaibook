package com.kadaibook.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiExtractionEnvelope(
        String status,
        JsonNode extraction,
        String error,
        int attempts,
        long latencyMs,
        String model,
        String promptVersion,
        String schemaVersion,
        long inputTokens,
        long outputTokens,
        long costPaise,
        String layoutType,
        double layoutConfidence
) {
}
