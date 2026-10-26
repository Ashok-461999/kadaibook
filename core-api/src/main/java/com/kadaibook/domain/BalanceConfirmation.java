package com.kadaibook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "balance_confirmations")
@Getter
@Setter
@NoArgsConstructor
public class BalanceConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private NotebookPage page;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "confirmed_balance_paise", nullable = false)
    private long confirmedBalancePaise;

    @Column(name = "calculated_balance_paise", nullable = false)
    private long calculatedBalancePaise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confirmed_by", nullable = false)
    private User confirmedBy;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @PrePersist
    void onCreate() {
        if (confirmedAt == null) {
            confirmedAt = Instant.now();
        }
    }
}
