package com.kadaibook.repository;

import com.kadaibook.domain.ExtractionRun;
import com.kadaibook.domain.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, UUID> {

    Optional<ExtractionRun> findTopByPageIdOrderByCreatedAtDesc(UUID pageId);

    List<ExtractionRun> findAllByPageIdOrderByCreatedAtDesc(UUID pageId);

    List<ExtractionRun> findAllByStatusAndCreatedAtBefore(RunStatus status, Instant cutoff);

    void deleteAllByPageId(UUID pageId);
}
