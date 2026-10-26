package com.kadaibook.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(String allowedOrigins) {

    public List<String> origins() {
        return allowedOrigins == null || allowedOrigins.isBlank()
                ? List.of("http://localhost:5173")
                : java.util.Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
    }
}
