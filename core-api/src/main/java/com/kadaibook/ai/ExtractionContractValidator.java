package com.kadaibook.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ExtractionContractValidator {

    private final JsonSchema schema;

    public ExtractionContractValidator(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = new ClassPathResource("contracts/extraction-schema-v1.0.json").getInputStream()) {
            JsonNode schemaNode = objectMapper.readTree(input);
            this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
        }
    }

    public void validate(JsonNode payload, String expectedPageId) {
        Set<ValidationMessage> errors = schema.validate(payload);
        if (!errors.isEmpty()) {
            String summary = errors.stream()
                    .limit(5)
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
            throw new ExtractionContractException("extraction does not match schema v1.0: " + summary);
        }
        JsonNode pageId = payload.get("page_id");
        if (pageId == null || !expectedPageId.equals(pageId.asText())) {
            throw new ExtractionContractException("extraction page_id does not match the requested page");
        }
        try {
            UUID.fromString(pageId.asText());
        } catch (IllegalArgumentException exception) {
            throw new ExtractionContractException("extraction page_id is not a UUID");
        }
        if (!"INR".equals(payload.path("currency").asText())) {
            throw new ExtractionContractException("extraction currency must be INR");
        }
        if (!"1.0".equals(payload.path("schema_version").asText())) {
            throw new ExtractionContractException("unsupported extraction schema_version");
        }
    }

    public static class ExtractionContractException extends RuntimeException {
        public ExtractionContractException(String message) {
            super(message);
        }
    }
}
