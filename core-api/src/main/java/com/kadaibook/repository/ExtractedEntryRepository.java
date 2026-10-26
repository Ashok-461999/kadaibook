package com.kadaibook.repository;

import com.kadaibook.domain.ExtractedEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedEntryRepository extends JpaRepository<ExtractedEntry, UUID> {

    List<ExtractedEntry> findAllByRunIdOrderByEntryIndexAsc(UUID runId);

    Optional<ExtractedEntry> findByIdAndRunPageId(UUID id, UUID pageId);

    long countByRunId(UUID runId);

    long countByRunPageId(UUID pageId);

    void deleteAllByRunId(UUID runId);

    void deleteAllByRunPageId(UUID pageId);
}
