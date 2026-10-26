package com.kadaibook.service;

import com.kadaibook.domain.BalanceConfirmation;
import com.kadaibook.domain.Customer;
import com.kadaibook.domain.ExtractedEntry;
import com.kadaibook.domain.ExtractionRun;
import com.kadaibook.domain.NotebookPage;
import com.kadaibook.domain.ReviewedEntry;
import com.kadaibook.repository.BalanceConfirmationRepository;
import com.kadaibook.repository.CustomerRepository;
import com.kadaibook.repository.ExtractedEntryRepository;
import com.kadaibook.repository.ExtractionRunRepository;
import com.kadaibook.repository.LedgerEntryRepository;
import com.kadaibook.repository.ReviewedEntryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReconciliationLoader {

    private final ExtractionRunRepository runRepository;
    private final ExtractedEntryRepository entryRepository;
    private final ReviewedEntryRepository reviewedRepository;
    private final CustomerRepository customerRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final BalanceConfirmationRepository confirmationRepository;
    private final ReconciliationEngine reconciliationEngine;

    public ReconciliationLoader(
            ExtractionRunRepository runRepository,
            ExtractedEntryRepository entryRepository,
            ReviewedEntryRepository reviewedRepository,
            CustomerRepository customerRepository,
            LedgerEntryRepository ledgerRepository,
            BalanceConfirmationRepository confirmationRepository,
            ReconciliationEngine reconciliationEngine) {
        this.runRepository = runRepository;
        this.entryRepository = entryRepository;
        this.reviewedRepository = reviewedRepository;
        this.customerRepository = customerRepository;
        this.ledgerRepository = ledgerRepository;
        this.confirmationRepository = confirmationRepository;
        this.reconciliationEngine = reconciliationEngine;
    }

    @Transactional(readOnly = true)
    public Loaded load(NotebookPage page) {
        ExtractionRun run = runRepository.findTopByPageIdOrderByCreatedAtDesc(page.getId()).orElse(null);
        List<ExtractedEntry> entries = run == null
                ? List.of()
                : entryRepository.findAllByRunIdOrderByEntryIndexAsc(run.getId());
        List<Customer> customers = customerRepository.findAllByShopIdOrderByDisplayNameAsc(page.getShop().getId());
        Map<UUID, ReviewedEntry> reviews = new HashMap<>();
        if (!entries.isEmpty()) {
            List<UUID> ids = entries.stream().map(ExtractedEntry::getId).toList();
            for (ReviewedEntry review : reviewedRepository.findAllForEntries(ids)) {
                reviews.put(review.getExtractedEntry().getId(), review);
            }
        }
        Map<UUID, Long> balances = new HashMap<>();
        Map<UUID, Long> counts = new HashMap<>();
        for (Customer customer : customers) {
            balances.put(customer.getId(), ledgerRepository.sumBalanceExcludingPage(
                    page.getShop().getId(), customer.getId(), page.getId()));
            counts.put(customer.getId(), ledgerRepository.countExcludingPage(
                    page.getShop().getId(), customer.getId(), page.getId()));
        }
        List<BalanceConfirmation> confirmations = confirmationRepository.findAllByPageId(page.getId());
        ReconciliationEngine.Plan plan = reconciliationEngine.calculate(
                page, run, entries, customers, reviews, balances, counts, confirmations);
        return new Loaded(run, List.copyOf(entries), List.copyOf(confirmations), plan);
    }

    public record Loaded(
            ExtractionRun run,
            List<ExtractedEntry> entries,
            List<BalanceConfirmation> confirmations,
            ReconciliationEngine.Plan plan
    ) {
    }
}
