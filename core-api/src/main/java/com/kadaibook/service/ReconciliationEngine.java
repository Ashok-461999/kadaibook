package com.kadaibook.service;

import com.kadaibook.domain.BalanceConfirmation;
import com.kadaibook.domain.Customer;
import com.kadaibook.domain.EntryStatus;
import com.kadaibook.domain.ExtractedEntry;
import com.kadaibook.domain.ExtractionRun;
import com.kadaibook.domain.LayoutType;
import com.kadaibook.domain.MatchStatus;
import com.kadaibook.domain.NotebookPage;
import com.kadaibook.domain.PageStatus;
import com.kadaibook.domain.ReviewedEntry;
import com.kadaibook.domain.ReviewAction;
import com.kadaibook.domain.ValueStatus;
import com.kadaibook.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationEngine {

    private final CustomerMatcher customerMatcher;

    public ReconciliationEngine(CustomerMatcher customerMatcher) {
        this.customerMatcher = customerMatcher;
    }

    public Plan calculate(
            NotebookPage page,
            ExtractionRun run,
            List<ExtractedEntry> entries,
            List<Customer> customers,
            Map<UUID, ReviewedEntry> reviews,
            Map<UUID, Long> existingBalances,
            Map<UUID, Long> existingEntryCounts,
            List<BalanceConfirmation> confirmations) {
        Map<UUID, Customer> customersById = new HashMap<>();
        customers.forEach(customer -> customersById.put(customer.getId(), customer));

        CustomerMatcher.Match pageMatch = run == null
                ? new CustomerMatcher.Match(MatchStatus.NONE, List.of())
                : customerMatcher.match(customers, readable(run.getPageCustomerName(), run.getPageCustomerStatus()));
        Customer openingCustomer = page.getLayoutType() == LayoutType.CUSTOMER_LEDGER ? pageMatch.single() : null;
        long opening = run == null || run.getOpeningBalancePaise() == null ? 0 : run.getOpeningBalancePaise();
        boolean priorLedger = openingCustomer != null
                && existingEntryCounts.getOrDefault(openingCustomer.getId(), 0L) > 0;
        boolean applyOpening = openingCustomer != null && !priorLedger && opening != 0;

        Map<UUID, Long> running = new HashMap<>();
        for (Customer customer : customers) {
            running.put(customer.getId(), existingBalances.getOrDefault(customer.getId(), 0L));
        }
        if (applyOpening) {
            running.put(openingCustomer.getId(), Money.checkedAdd(running.get(openingCustomer.getId()), opening));
        }

        List<Row> rows = new ArrayList<>();
        List<Blocker> blockers = new ArrayList<>();
        if (run == null) {
            blockers.add(new Blocker("not_extracted", "The page has not been extracted yet.", null, null));
        }

        for (ExtractedEntry entry : entries) {
            ReviewedEntry review = reviews.get(entry.getId());
            String candidateName = customerCandidateName(entry, run);
            CustomerMatcher.Match candidateMatch = customerMatcher.match(customers, candidateName);
            Customer customer = review != null && review.getCustomer() != null
                    ? review.getCustomer()
                    : candidateMatch.single();
            MatchStatus matchStatus = review != null && review.getCustomer() != null
                    ? MatchStatus.MATCHED
                    : candidateMatch.status();
            List<Customer> candidates = customer != null ? List.of(customer) : candidateMatch.customers();

            EntryStatus status = effectiveEntryStatus(entry, review);
            LocalDate date = review != null && review.getFinalDate() != null ? review.getFinalDate() : entry.getEntryDate();
            ValueStatus dateStatus = review != null && review.getFinalDateStatus() != null
                    ? review.getFinalDateStatus()
                    : entry.getDateStatus();
            String description = review != null && review.getFinalDescription() != null
                    ? review.getFinalDescription()
                    : entry.getDescription();
            Long debit = review != null && review.getFinalDebitPaise() != null
                    ? review.getFinalDebitPaise()
                    : entry.getDebitPaise();
            Long credit = review != null && review.getFinalCreditPaise() != null
                    ? review.getFinalCreditPaise()
                    : entry.getCreditPaise();
            String customerName = customer != null
                    ? customer.getDisplayName()
                    : (candidateName == null ? "" : candidateName);

            boolean candidate = status.importable();
            boolean amountValid = validAmount(debit, credit);
            if (candidate) {
                if (review == null) {
                    blockers.add(new Blocker(
                            "review_pending",
                            reviewMessage(entry),
                            entry.getId(),
                            entry.getEntryIndex()));
                }
                if (!amountValid) {
                    blockers.add(new Blocker(
                            "invalid_amount",
                            "Enter a debit or credit amount greater than zero.",
                            entry.getId(),
                            entry.getEntryIndex()));
                }
                if (customer == null) {
                    blockers.add(new Blocker(
                            "missing_customer",
                            matchStatus == MatchStatus.AMBIGUOUS
                                    ? "Choose which customer this entry belongs to."
                                    : "Assign this entry to a customer.",
                            entry.getId(),
                            entry.getEntryIndex()));
                }
                if (date == null) {
                    blockers.add(new Blocker(
                            "missing_date",
                            "Enter a date for this entry.",
                            entry.getId(),
                            entry.getEntryIndex()));
                }
            }
            boolean importable = candidate && amountValid && customer != null && date != null;
            Long calculated = null;
            boolean balanceMismatch = false;
            if (importable) {
                long next = Money.checkedAdd(
                        running.getOrDefault(customer.getId(), 0L),
                        Money.amountDelta(debit, credit));
                running.put(customer.getId(), next);
                calculated = next;
                balanceMismatch = entry.getRunningBalancePaise() != null && entry.getRunningBalancePaise() != next;
            }
            rows.add(new Row(
                    entry,
                    review,
                    customer,
                    matchStatus,
                    candidates,
                    customerName,
                    date,
                    dateStatus,
                    description,
                    debit,
                    credit,
                    status,
                    calculated,
                    balanceMismatch,
                    importable,
                    candidate && review == null));
        }

        Map<UUID, Long> finalBalances = new LinkedHashMap<>(running);
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (Row row : rows) {
            if (row.importable()) {
                counts.merge(row.customer().getId(), 1, Integer::sum);
            }
        }
        Customer closingCustomer = openingCustomer;
        if (closingCustomer == null && counts.size() == 1) {
            closingCustomer = customersById.get(counts.keySet().iterator().next());
        }
        Map<UUID, BalanceConfirmation> confirmationMap = new HashMap<>();
        confirmations.forEach(confirmation -> confirmationMap.put(confirmation.getCustomer().getId(), confirmation));

        List<Preview> previews = new ArrayList<>();
        List<Blocker> confirmationBlockers = new ArrayList<>();
        for (Map.Entry<UUID, Integer> count : counts.entrySet()) {
            Customer customer = customersById.get(count.getKey());
            if (customer == null) {
                continue;
            }
            long existing = existingBalances.getOrDefault(customer.getId(), 0L);
            boolean openingIncluded = applyOpening && openingCustomer.equals(customer);
            Long writtenClosing = customer.equals(closingCustomer) && run != null
                    ? run.getClosingBalancePaise()
                    : null;
            long calculated = finalBalances.getOrDefault(customer.getId(), existing);
            boolean closingMismatch = writtenClosing != null && writtenClosing != calculated;
            BalanceConfirmation confirmation = confirmationMap.get(customer.getId());
            Preview preview = new Preview(
                    customer,
                    count.getValue(),
                    existing,
                    openingIncluded,
                    openingIncluded ? opening : null,
                    openingIncluded,
                    calculated,
                    writtenClosing,
                    closingMismatch,
                    confirmation == null ? null : confirmation.getConfirmedBalancePaise());
            previews.add(preview);
            if (confirmation == null) {
                confirmationBlockers.add(new Blocker(
                        "confirmation_missing",
                        "Confirm the calculated balance for " + customer.getDisplayName() + ".",
                        null,
                        null));
            } else if (confirmation.getConfirmedBalancePaise() != calculated) {
                confirmationBlockers.add(new Blocker(
                        "confirmation_mismatch",
                        "The confirmed balance for " + customer.getDisplayName() + " no longer matches the calculation.",
                        null,
                        null));
            }
        }
        previews.sort(Comparator.comparing(preview -> preview.customer().getDisplayName()));

        if (run != null && run.getStatus() == com.kadaibook.domain.RunStatus.SUCCESS && previews.isEmpty()) {
            blockers.add(new Blocker(
                    "no_importable_entries",
                    "There are no entries that can be imported from this page.",
                    null,
                    null));
        }

        List<String> warnings = new ArrayList<>();
        if (page.getImageDeletedAt() != null) {
            warnings.add("The page image has been deleted by the retention policy.");
        }
        if (previews.stream().anyMatch(Preview::closingMismatch)) {
            warnings.add("The written closing balance does not match the calculated balance.");
        }
        if (rows.stream().anyMatch(Row::balanceMismatch)) {
            warnings.add("One or more rows differ from the running balance written on the page.");
        }
        if (page.getLayoutType() == LayoutType.DATE_WISE && run != null && run.getOpeningBalancePaise() != null) {
            warnings.add("An opening balance is not imported for a date-wise page.");
        }

        boolean canConfirm = page.getStatus() == PageStatus.NEEDS_REVIEW
                && !previews.isEmpty()
                && blockers.isEmpty();

        return new Plan(
                List.copyOf(rows),
                List.copyOf(previews),
                List.copyOf(blockers),
                List.copyOf(confirmationBlockers),
                List.copyOf(warnings),
                applyOpening,
                openingCustomer,
                opening,
                canConfirm);
    }

    private static String customerCandidateName(ExtractedEntry entry, ExtractionRun run) {
        String entryName = readable(entry.getCustomerNameValue(), entry.getCustomerNameStatus());
        if (entryName != null) {
            return entryName;
        }
        return run == null ? null : readable(run.getPageCustomerName(), run.getPageCustomerStatus());
    }

    private static String readable(String value, ValueStatus status) {
        if (status == ValueStatus.UNREADABLE || value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static EntryStatus effectiveEntryStatus(ExtractedEntry entry, ReviewedEntry review) {
        if (review != null && review.getAction() == ReviewAction.REJECTED) {
            return EntryStatus.REJECTED;
        }
        if (review != null && review.getFinalEntryStatus() != null) {
            return review.getFinalEntryStatus();
        }
        return entry.getEntryStatus();
    }

    private static boolean validAmount(Long debit, Long credit) {
        try {
            Money.requireImportableAmount(debit, credit);
            return true;
        } catch (Money.InvalidMoneyException exception) {
            return false;
        }
    }

    private static String reviewMessage(ExtractedEntry entry) {
        if (entry.getIssues() != null && !entry.getIssues().isEmpty()) {
            return "Review the flagged fields: " + String.join(", ", entry.getIssues()) + ".";
        }
        if (entry.getEntryStatus() == EntryStatus.UNCERTAIN) {
            return "Confirm this uncertain entry.";
        }
        return "Review this entry before importing.";
    }

    public record Plan(
            List<Row> rows,
            List<Preview> previews,
            List<Blocker> blockers,
            List<Blocker> confirmationBlockers,
            List<String> warnings,
            boolean applyOpening,
            Customer openingCustomer,
            long openingBalancePaise,
            boolean canConfirm
    ) {

        public List<Blocker> allBlockers() {
            List<Blocker> all = new ArrayList<>(blockers);
            all.addAll(confirmationBlockers);
            return all;
        }
    }

    public record Row(
            ExtractedEntry entry,
            ReviewedEntry review,
            Customer customer,
            MatchStatus matchStatus,
            List<Customer> candidates,
            String customerName,
            LocalDate date,
            ValueStatus dateStatus,
            String description,
            Long debitPaise,
            Long creditPaise,
            EntryStatus entryStatus,
            Long calculatedBalancePaise,
            boolean balanceMismatch,
            boolean importable,
            boolean reviewPending
    ) {
    }

    public record Preview(
            Customer customer,
            int entryCount,
            long existingBalancePaise,
            boolean openingImported,
            Long openingBalancePaise,
            boolean openingApplied,
            long calculatedBalancePaise,
            Long writtenClosingBalancePaise,
            boolean closingMismatch,
            Long confirmedBalancePaise
    ) {
    }

    public record Blocker(String code, String message, UUID entryId, Integer entryIndex) {
    }
}
