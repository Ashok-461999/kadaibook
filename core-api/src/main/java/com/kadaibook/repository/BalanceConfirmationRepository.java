package com.kadaibook.repository;

import com.kadaibook.domain.BalanceConfirmation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceConfirmationRepository extends JpaRepository<BalanceConfirmation, UUID> {

    List<BalanceConfirmation> findAllByPageId(UUID pageId);

    Optional<BalanceConfirmation> findByPageIdAndCustomerId(UUID pageId, UUID customerId);

    void deleteAllByPageId(UUID pageId);
}
