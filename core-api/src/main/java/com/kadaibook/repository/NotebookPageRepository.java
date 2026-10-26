package com.kadaibook.repository;

import com.kadaibook.domain.NotebookPage;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotebookPageRepository extends JpaRepository<NotebookPage, UUID> {

    List<NotebookPage> findAllByShopIdOrderByCreatedAtDesc(UUID shopId);

    Optional<NotebookPage> findByIdAndShopId(UUID id, UUID shopId);

    Optional<NotebookPage> findByShopIdAndImageSha256(UUID shopId, String imageSha256);

    List<NotebookPage> findTop100ByImageDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
            java.time.Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from NotebookPage p where p.id = :id and p.shop.id = :shopId")
    Optional<NotebookPage> findForUpdate(@Param("id") UUID id, @Param("shopId") UUID shopId);
}
