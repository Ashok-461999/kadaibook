package com.kadaibook.service;

import com.kadaibook.config.RetentionProperties;
import com.kadaibook.domain.NotebookPage;
import com.kadaibook.repository.NotebookPageRepository;
import com.kadaibook.storage.StorageService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.retention.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final RetentionProperties properties;
    private final NotebookPageRepository pageRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public RetentionService(
            RetentionProperties properties,
            NotebookPageRepository pageRepository,
            StorageService storageService,
            AuditService auditService) {
        this.properties = properties;
        this.pageRepository = pageRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    @Transactional
    public void deleteExpiredImages() {
        int days = Math.max(1, properties.days());
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        List<NotebookPage> expired = pageRepository
                .findTop100ByImageDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(cutoff);
        int deleted = 0;
        for (NotebookPage page : expired) {
            try {
                storageService.delete(page.getS3Key());
                page.setImageDeletedAt(Instant.now());
                pageRepository.save(page);
                auditService.record(
                        page.getShop(),
                        null,
                        "page.image_deleted",
                        "page",
                        page.getId(),
                        null,
                        java.util.Map.of(
                                "created_at", page.getCreatedAt().toString(),
                                "retention_days", days));
                deleted++;
            } catch (RuntimeException exception) {
                log.warn("could not delete retained image for page {}", page.getId(), exception);
            }
        }
        if (deleted > 0) {
            log.info("retention deleted {} page images older than {} days", deleted, days);
        }
    }
}
