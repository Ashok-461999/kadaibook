package com.kadaibook.config;

import com.kadaibook.storage.LocalStorageService;
import com.kadaibook.storage.S3StorageService;
import com.kadaibook.storage.StorageService;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.storage.backend", havingValue = "local", matchIfMissing = true)
    public StorageService localStorageService(StorageProperties properties) {
        log.info("using local encrypted-at-rest-by-host storage root {}", properties.localRoot());
        return new LocalStorageService(Path.of(properties.localRoot()));
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.backend", havingValue = "s3")
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        if (s3 == null || s3.bucket() == null || s3.bucket().isBlank()) {
            throw new IllegalStateException("S3 bucket is required when STORAGE_BACKEND=s3");
        }
        if (s3.kmsKeyId() == null || s3.kmsKeyId().isBlank()) {
            throw new IllegalStateException("S3_KMS_KEY_ID is required: page images must be KMS encrypted");
        }
        return S3Client.builder().region(Region.of(s3.region())).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.backend", havingValue = "s3")
    public StorageService s3StorageService(S3Client s3Client, StorageProperties properties) {
        StorageProperties.S3 s3 = properties.s3();
        log.info("using S3 storage bucket {} with KMS encryption", s3.bucket());
        return new S3StorageService(s3Client, s3.bucket(), s3.kmsKeyId());
    }
}
