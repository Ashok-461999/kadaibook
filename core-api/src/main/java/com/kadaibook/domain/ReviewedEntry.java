package com.kadaibook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "reviewed_entries")
@Getter
@Setter
@NoArgsConstructor
public class ReviewedEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extracted_entry_id", nullable = false, unique = true)
    private ExtractedEntry extractedEntry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewAction action;

    @Column(name = "final_date")
    private LocalDate finalDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_date_status", length = 20)
    private ValueStatus finalDateStatus;

    @Column(name = "final_description", columnDefinition = "text")
    private String finalDescription;

    @Column(name = "final_debit_paise")
    private Long finalDebitPaise;

    @Column(name = "final_credit_paise")
    private Long finalCreditPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_entry_status", length = 20)
    private EntryStatus finalEntryStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @PrePersist
    void onCreate() {
        if (decidedAt == null) {
            decidedAt = Instant.now();
        }
    }
}
