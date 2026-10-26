package com.kadaibook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "debit_paise", nullable = false)
    private long debitPaise;

    @Column(name = "credit_paise", nullable = false)
    private long creditPaise;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_reviewed_entry_id", unique = true)
    private ReviewedEntry sourceReviewedEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_page_id")
    private NotebookPage sourcePage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
