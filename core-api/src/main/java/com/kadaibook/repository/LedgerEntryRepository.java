package com.kadaibook.repository;

import com.kadaibook.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query("""
            select coalesce(sum(l.debitPaise - l.creditPaise), 0)
            from LedgerEntry l
            where l.shop.id = :shopId and l.customer.id = :customerId
            """)
    long sumBalance(@Param("shopId") UUID shopId, @Param("customerId") UUID customerId);

    @Query("""
            select coalesce(sum(l.debitPaise - l.creditPaise), 0)
            from LedgerEntry l
            where l.shop.id = :shopId and l.customer.id = :customerId
              and (l.sourcePage is null or l.sourcePage.id <> :pageId)
            """)
    long sumBalanceExcludingPage(
            @Param("shopId") UUID shopId,
            @Param("customerId") UUID customerId,
            @Param("pageId") UUID pageId);

    @Query("""
            select count(l)
            from LedgerEntry l
            where l.shop.id = :shopId and l.customer.id = :customerId
              and (l.sourcePage is null or l.sourcePage.id <> :pageId)
            """)
    long countExcludingPage(
            @Param("shopId") UUID shopId,
            @Param("customerId") UUID customerId,
            @Param("pageId") UUID pageId);

    long countByShopIdAndCustomerId(UUID shopId, UUID customerId);

    @Query("""
            select l from LedgerEntry l
            where l.shop.id = :shopId and l.customer.id = :customerId
            order by l.entryDate asc nulls first, l.createdAt asc
            """)
    List<LedgerEntry> findLedger(
            @Param("shopId") UUID shopId,
            @Param("customerId") UUID customerId);

    List<LedgerEntry> findAllBySourcePageIdOrderByCreatedAtAsc(UUID pageId);
}
