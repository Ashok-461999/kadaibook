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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "extraction_runs")
@Getter
@Setter
@NoArgsConstructor
public class ExtractionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private NotebookPage page;

    @Column(length = 160)
    private String model;

    @Column(name = "prompt_version", length = 60)
    private String promptVersion;

    @Column(name = "schema_version", length = 20)
    private String schemaVersion;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "cost_paise", nullable = false)
    private long costPaise;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RunStatus status;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "layout_confidence")
    private Double layoutConfidence;

    @Column(name = "opening_balance_paise")
    private Long openingBalancePaise;

    @Column(name = "closing_balance_paise")
    private Long closingBalancePaise;

    @Column(name = "page_customer_name", length = 200)
    private String pageCustomerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "page_customer_status", length = 20)
    private ValueStatus pageCustomerStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "page_issues", nullable = false, columnDefinition = "jsonb")
    private List<String> pageIssues = new ArrayList<>();

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (pageIssues == null) {
            pageIssues = new ArrayList<>();
        }
    }
}
