package com.kadaibook.repository;

import com.kadaibook.domain.ReviewedEntry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewedEntryRepository extends JpaRepository<ReviewedEntry, UUID> {

    Optional<ReviewedEntry> findByExtractedEntryId(UUID extractedEntryId);

    @Query("select r from ReviewedEntry r where r.extractedEntry.id in :ids")
    List<ReviewedEntry> findAllForEntries(@Param("ids") Collection<UUID> ids);

    @Query("select count(r) from ReviewedEntry r where r.extractedEntry.run.page.id = :pageId")
    long countForPage(@Param("pageId") UUID pageId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from ReviewedEntry r where r.extractedEntry.run.page.id = :pageId")
    int deleteForPage(@Param("pageId") UUID pageId);
}
