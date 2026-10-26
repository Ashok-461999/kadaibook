package com.kadaibook.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kadaibook.config.AiProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    private static final int MAX_ERROR_LENGTH = 300;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public AiServiceClient(AiProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.maxAttempts = Math.max(1, properties.maxTransportAttempts());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout() == null
                ? Duration.ofSeconds(5) : properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout() == null
                ? Duration.ofSeconds(120) : properties.readTimeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory);
        if (properties.internalToken() != null && !properties.internalToken().isBlank()) {
            builder.defaultHeader("X-Internal-Token", properties.internalToken());
        }
        this.restClient = builder.build();
    }

    public AiExtractionEnvelope extract(
            UUID pageId,
            UUID runId,
            byte[] image,
            String filename,
            String contentType) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call(pageId, runId, image, filename, contentType);
            } catch (AiServiceException exception) {
                if (exception.kind() == AiServiceException.Kind.TRANSPORT && attempt < maxAttempts) {
                    last = exception;
                    sleep(attempt);
                    continue;
                }
                throw exception;
            }
        }
        throw last == null
                ? new AiServiceException(AiServiceException.Kind.TRANSPORT, "AI service request failed")
                : last;
    }

    private AiExtractionEnvelope call(
            UUID pageId,
            UUID runId,
            byte[] image,
            String filename,
            String contentType) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("page_id", pageId.toString());
        body.part("run_id", runId.toString());
        body.part("file", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(MediaType.parseMediaType(contentType));

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri("/extract")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .toEntity(String.class);
            String raw = response.getBody();
            if (raw == null || raw.isBlank()) {
                throw new AiServiceException(
                        AiServiceException.Kind.INVALID_RESPONSE, "AI service returned an empty response");
            }
            return objectMapper.readValue(raw, AiExtractionEnvelope.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AiServiceException(
                        AiServiceException.Kind.AUTH, "AI service rejected the internal service token");
            }
            if (status == 429 || status >= 500) {
                log.warn("AI service unavailable (HTTP {})", status);
                throw new AiServiceException(
                        AiServiceException.Kind.TRANSPORT, "AI service unavailable (HTTP " + status + ")");
            }
            throw new AiServiceException(
                    AiServiceException.Kind.REJECTED,
                    "AI service rejected the request (HTTP " + status + ")");
        } catch (ResourceAccessException exception) {
            log.warn("AI service transport failure: {}", exception.getClass().getSimpleName());
            throw new AiServiceException(
                    AiServiceException.Kind.TRANSPORT, "AI service could not be reached");
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AiServiceException(
                    AiServiceException.Kind.INVALID_RESPONSE, "AI service returned malformed JSON");
        } catch (RestClientException exception) {
            throw new AiServiceException(
                    AiServiceException.Kind.INVALID_RESPONSE, "AI service response could not be read");
        }
    }

    private void sleep(int attempt) {
        try {
            Thread.sleep(Math.min(4000L, 750L * attempt));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiServiceException(
                    AiServiceException.Kind.TRANSPORT, "interrupted while retrying AI service");
        }
    }
}
