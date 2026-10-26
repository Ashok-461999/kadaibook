package com.kadaibook.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String baseUrl,
        String internalToken,
        Duration connectTimeout,
        Duration readTimeout,
        int maxTransportAttempts
) {
}
