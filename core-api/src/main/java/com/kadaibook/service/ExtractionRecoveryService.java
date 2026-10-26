package com.kadaibook.service;

import com.kadaibook.domain.ExtractionRun;
import com.kadaibook.domain.NotebookPage;
import com.kadaibook.domain.PageStatus;
import com.kadaibook.domain.RunStatus;
import com.kadaibook.repository.ExtractionRunRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExtractionRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionRecoveryService.class);

    private final ExtractionRunRepository runRepository;
    private final AuditService auditService;

    public ExtractionRecoveryService(ExtractionRunRepository runRepository, AuditService auditService) {
        this.runRepository = runRepository;
        this.auditService = auditService;
    }

    @Scheduled(fixedDelayString = "${app.recovery.interval-ms:300000}", initialDelayString = "60000")
    @Transactional
    public void recoverStaleRuns() {
        Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
        List<ExtractionRun> stale = runRepository.findAllByStatusAndCreatedAtBefore(RunStatus.RUNNING, cutoff);
        for (ExtractionRun run : stale) {
            run.setStatus(RunStatus.TRANSPORT_ERROR);
            run.setError("The extraction timed out before it completed.");
            NotebookPage page = run.getPage();
            if (page.getStatus() == PageStatus.EXTRACTING) {
                page.setStatus(PageStatus.EXTRACTION_FAILED);
            }
            auditService.record(
                    page.getShop(),
                    null,
                    "extraction.recovered",
                    "page",
                    page.getId(),
                    Map.of("status", PageStatus.EXTRACTING.wire()),
                    Map.of("status", PageStatus.EXTRACTION_FAILED.wire(),
                            "run_id", run.getId().toString()));
            log.warn("recovered stale extraction run {}", run.getId());
        }
    }
}
