package com.kadaibook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(long maxBytes, long maxPixels) {
}
