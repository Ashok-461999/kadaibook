package com.kadaibook.repository;

import com.kadaibook.domain.Shop;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, UUID> {

    List<Shop> findAllByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    Optional<Shop> findByIdAndOwnerId(UUID id, UUID ownerId);
}
