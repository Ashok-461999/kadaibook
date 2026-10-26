package com.kadaibook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String backend, String localRoot, S3 s3) {

    public record S3(String bucket, String region, String kmsKeyId) {
    }
}
