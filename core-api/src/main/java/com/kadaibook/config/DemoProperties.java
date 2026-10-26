package com.kadaibook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(
        boolean seed,
        String phone,
        String password,
        String displayName,
        String shopName
) {
}
