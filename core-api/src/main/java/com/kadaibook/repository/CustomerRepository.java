package com.kadaibook.repository;

import com.kadaibook.domain.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findAllByShopIdOrderByDisplayNameAsc(UUID shopId);

    Optional<Customer> findByIdAndShopId(UUID id, UUID shopId);

    boolean existsByShopIdAndNormalizedName(UUID shopId, String normalizedName);

    Optional<Customer> findByShopIdAndNormalizedName(UUID shopId, String normalizedName);

    long countByShopId(UUID shopId);
}
