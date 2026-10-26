package com.kadaibook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(boolean enabled, int days, String cron) {
}
