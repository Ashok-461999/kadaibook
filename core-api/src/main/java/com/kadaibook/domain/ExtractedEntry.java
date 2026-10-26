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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "extracted_entries")
@Getter
@Setter
@NoArgsConstructor
public class ExtractedEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ExtractionRun run;

    @Column(name = "entry_index", nullable = false)
    private int entryIndex;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_status", nullable = false, length = 20)
    private ValueStatus dateStatus;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "debit_paise")
    private Long debitPaise;

    @Column(name = "credit_paise")
    private Long creditPaise;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_status", nullable = false, length = 20)
    private EntryStatus entryStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> issues = new ArrayList<>();

    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    @Column(name = "running_balance_paise")
    private Long runningBalancePaise;

    @Column(name = "customer_name_value", length = 200)
    private String customerNameValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_name_status", length = 20)
    private ValueStatus customerNameStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (issues == null) {
            issues = new ArrayList<>();
        }
    }
}
