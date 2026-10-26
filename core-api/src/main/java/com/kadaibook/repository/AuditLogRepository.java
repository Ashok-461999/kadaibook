package com.kadaibook.repository;

import com.kadaibook.domain.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findAllByShopIdOrderByAtDesc(UUID shopId, Pageable pageable);
}
