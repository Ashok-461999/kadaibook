package com.kadaibook.service;

import com.kadaibook.domain.BalanceConfirmation;
import com.kadaibook.domain.Customer;
import com.kadaibook.domain.EntryStatus;
import com.kadaibook.domain.ExtractedEntry;
import com.kadaibook.domain.ExtractionRun;
import com.kadaibook.domain.LedgerEntry;
import com.kadaibook.domain.MatchStatus;
import com.kadaibook.domain.NotebookPage;
import com.kadaibook.domain.PageStatus;
import com.kadaibook.domain.ReviewedEntry;
import com.kadaibook.domain.ReviewAction;
import com.kadaibook.domain.User;
import com.kadaibook.domain.ValueStatus;
import com.kadaibook.money.Money;
import com.kadaibook.repository.BalanceConfirmationRepository;
import com.kadaibook.repository.CustomerRepository;
import com.kadaibook.repository.ExtractedEntryRepository;
import com.kadaibook.repository.ExtractionRunRepository;
import com.kadaibook.repository.LedgerEntryRepository;
import com.kadaibook.repository.NotebookPageRepository;
import com.kadaibook.repository.ReviewedEntryRepository;
import com.kadaibook.repository.UserRepository;
import com.kadaibook.security.UserPrincipal;
import com.kadaibook.web.ApiException;
import com.kadaibook.web.dto.PageDtos.BalanceConfirmationInput;
import com.kadaibook.web.dto.PageDtos.BulkConfirmRequest;
import com.kadaibook.web.dto.PageDtos.ConfirmRequest;
import com.kadaibook.web.dto.PageDtos.PageDetail;
import com.kadaibook.web.dto.PageDtos.ReviewRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final NotebookPageRepository pageRepository;
    private final ExtractionRunRepository runRepository;
    private final ExtractedEntryRepository entryRepository;
    private final ReviewedEntryRepository reviewedRepository;
    private final CustomerRepository customerRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final BalanceConfirmationRepository confirmationRepository;
    private final UserRepository userRepository;
    private final CustomerMatcher customerMatcher;
    private final ReconciliationLoader reconciliationLoader;
    private final PageViewService pageViewService;
    private final AuditService auditService;

    public ReviewService(
            NotebookPageRepository pageRepository,
            ExtractionRunRepository runRepository,
            ExtractedEntryRepository entryRepository,
            ReviewedEntryRepository reviewedRepository,
            CustomerRepository customerRepository,
            LedgerEntryRepository ledgerRepository,
            BalanceConfirmationRepository confirmationRepository,
            UserRepository userRepository,
            CustomerMatcher customerMatcher,
            ReconciliationLoader reconciliationLoader,
            PageViewService pageViewService,
            AuditService auditService) {
        this.pageRepository = pageRepository;
        this.runRepository = runRepository;
        this.entryRepository = entryRepository;
        this.reviewedRepository = reviewedRepository;
        this.customerRepository = customerRepository;
        this.ledgerRepository = ledgerRepository;
        this.confirmationRepository = confirmationRepository;
        this.userRepository = userRepository;
        this.customerMatcher = customerMatcher;
        this.reconciliationLoader = reconciliationLoader;
        this.pageViewService = pageViewService;
        this.auditService = auditService;
    }

    @Transactional
    public PageDetail review(
            UUID shopId,
            UUID pageId,
            UUID entryId,
            ReviewRequest request,
            UserPrincipal principal) {
        NotebookPage page = pageRepository.findForUpdate(pageId, shopId)
                .orElseThrow(() -> ApiException.notFound("Page not found"));
        if (page.getStatus() != PageStatus.NEEDS_REVIEW) {
            throw ApiException.conflict("page_not_reviewable", "This page cannot be reviewed in its current state.");
        }
        ExtractedEntry entry = entryRepository.findByIdAndRunPageId(entryId, pageId)
                .orElseThrow(() -> ApiException.notFound("Entry not found"));
        ReviewedEntry existing = reviewedRepository.findByExtractedEntryId(entryId).orElse(null);
        ReviewAction action = request.action();
        if (action == null) {
            throw ApiException.badRequest("invalid_action", "Choose whether to confirm, correct, or reject the entry.");
        }
        Customer requestedCustomer = customer(request.customerId(), page);
        String name = readable(entry.getCustomerNameValue(), entry.getCustomerNameStatus());
        if (name == null) {
            ExtractionRun run = runRepository.findTopByPageIdOrderByCreatedAtDesc(pageId).orElse(null);
            if (run != null) {
                name = readable(run.getPageCustomerName(), run.getPageCustomerStatus());
            }
        }
        Customer matched = customerMatcher.match(
                        customerRepository.findAllByShopIdOrderByDisplayNameAsc(shopId), name)
                .single();

        ReviewedEntry review = existing == null ? new ReviewedEntry() : existing;
        review.setExtractedEntry(entry);
        review.setReviewer(userRepository.getReferenceById(principal.id()));
        review.setAction(action);
        review.setNote(note(request.note()));

        if (action == ReviewAction.REJECTED) {
            review.setFinalDate(entry.getEntryDate());
            review.setFinalDateStatus(entry.getDateStatus());
            review.setFinalDescription(entry.getDescription());
            review.setFinalDebitPaise(entry.getDebitPaise());
            review.setFinalCreditPaise(entry.getCreditPaise());
            review.setFinalEntryStatus(EntryStatus.REJECTED);
            review.setCustomer(existing == null ? null : existing.getCustomer());
        } else if (action == ReviewAction.CONFIRMED) {
            requireConfirmable(entry);
            review.setFinalDate(entry.getEntryDate());
            review.setFinalDateStatus(entry.getDateStatus());
            review.setFinalDescription(entry.getDescription());
            review.setFinalDebitPaise(entry.getDebitPaise());
            review.setFinalCreditPaise(entry.getCreditPaise());
            review.setFinalEntryStatus(entry.getEntryStatus());
            Customer customer = firstNonNull(
                    requestedCustomer,
                    existing == null ? null : existing.getCustomer(),
                    matched);
            if (customer == null) {
                customer = ensureCustomer(page, name, principal);
            }
            review.setCustomer(customer);
        } else {
            applyCorrection(review, entry, request);
            Customer customer = firstNonNull(
                    requestedCustomer,
                    matched,
                    existing == null ? null : existing.getCustomer());
            if (customer == null) {
                customer = ensureCustomer(page, name, principal);
            }
            review.setCustomer(customer);
        }
        review.setDecidedAt(Instant.now());
        review = reviewedRepository.save(review);
        auditService.record(
                page.getShop(),
                review.getReviewer(),
                "entry.reviewed",
                "extracted_entry",
                entry.getId(),
                before(existing),
                after(review));
        return pageViewService.detail(page);
    }

    @Transactional
    public PageDetail confirm(
            UUID shopId,
            UUID pageId,
            ConfirmRequest request,
            UserPrincipal principal) {
        NotebookPage page = pageRepository.findForUpdate(pageId, shopId)
                .orElseThrow(() -> ApiException.notFound("Page not found"));
        if (page.getStatus() != PageStatus.NEEDS_REVIEW) {
            throw ApiException.conflict("page_not_confirmable", "This page cannot be confirmed in its current state.");
        }
        ReconciliationLoader.Loaded loaded = reconciliationLoader.load(page);
        ReconciliationEngine.Plan plan = loaded.plan();
        if (!plan.blockers().isEmpty()) {
            ReconciliationEngine.Blocker blocker = plan.blockers().get(0);
            throw ApiException.conflict(blocker.code(), blocker.message());
        }
        if (plan.previews().isEmpty() || !plan.canConfirm()) {
            throw ApiException.conflict("page_not_ready", "Resolve the remaining page issues before confirming.");
        }

        Map<UUID, Long> submitted = new HashMap<>();
        for (BalanceConfirmationInput input : request.confirmations()) {
            if (submitted.putIfAbsent(input.customerId(), input.confirmedBalancePaise()) != null) {
                throw ApiException.badRequest("duplicate_confirmation", "A customer balance was confirmed twice.");
            }
        }
        if (submitted.size() != plan.previews().size()) {
            throw ApiException.badRequest(
                    "confirmation_count",
                    "Confirm exactly the customers listed in the preview.");
        }
        for (ReconciliationEngine.Preview preview : plan.previews()) {
            if (!submitted.containsKey(preview.customer().getId())) {
                throw ApiException.badRequest(
                        "confirmation_count",
                        "Confirm the balance for every customer in the preview.");
            }
            Long confirmed = submitted.get(preview.customer().getId());
            if (!Money.isValidSigned(confirmed)) {
                throw ApiException.badRequest("invalid_balance", "A confirmed balance is outside the supported range.");
            }
            if (confirmed != preview.calculatedBalancePaise()) {
                throw ApiException.conflict(
                        "balance_mismatch",
                        "The confirmed balance for " + preview.customer().getDisplayName()
                                + " does not match the calculated balance. Refresh and review the entries.");
            }
        }

        int imported = 0;
        for (ReconciliationEngine.Row row : plan.rows()) {
            if (!row.importable()) {
                continue;
            }
            Money.requireImportableAmount(row.debitPaise(), row.creditPaise());
            LedgerEntry ledger = new LedgerEntry();
            ledger.setShop(page.getShop());
            ledger.setCustomer(row.customer());
            ledger.setEntryDate(row.date());
            ledger.setDescription(row.description() == null || row.description().isBlank()
                    ? "Notebook entry"
                    : row.description());
            ledger.setDebitPaise(row.debitPaise() == null ? 0 : row.debitPaise());
            ledger.setCreditPaise(row.creditPaise() == null ? 0 : row.creditPaise());
            ledger.setSourceReviewedEntry(row.review());
            ledger.setSourcePage(page);
            ledgerRepository.save(ledger);
            imported++;
        }
        if (plan.applyOpening() && plan.openingCustomer() != null && plan.openingBalancePaise() != 0
                && plan.previews().stream().anyMatch(preview ->
                        preview.customer().getId().equals(plan.openingCustomer().getId()))) {
            long opening = plan.openingBalancePaise();
            LedgerEntry ledger = new LedgerEntry();
            ledger.setShop(page.getShop());
            ledger.setCustomer(plan.openingCustomer());
            ledger.setEntryDate(null);
            ledger.setDescription("Opening balance from notebook page");
            ledger.setDebitPaise(opening > 0 ? opening : 0);
            ledger.setCreditPaise(opening < 0 ? -opening : 0);
            ledger.setSourcePage(page);
            ledgerRepository.save(ledger);
            imported++;
        }

        User actor = userRepository.getReferenceById(principal.id());
        for (ReconciliationEngine.Preview preview : plan.previews()) {
            BalanceConfirmation confirmation = new BalanceConfirmation();
            confirmation.setShop(page.getShop());
            confirmation.setPage(page);
            confirmation.setCustomer(preview.customer());
            confirmation.setConfirmedBalancePaise(submitted.get(preview.customer().getId()));
            confirmation.setCalculatedBalancePaise(preview.calculatedBalancePaise());
            confirmation.setConfirmedBy(actor);
            confirmationRepository.save(confirmation);
        }
        page.setStatus(PageStatus.IMPORTED);
        page.setImportedAt(Instant.now());
        auditService.record(
                page.getShop(),
                actor,
                "page.imported",
                "page",
                page.getId(),
                Map.of("status", PageStatus.NEEDS_REVIEW.wire()),
                Map.of(
                        "status", PageStatus.IMPORTED.wire(),
                        "ledger_entries", imported,
                        "customers", plan.previews().size()));
        return pageViewService.detail(page);
    }

    @Transactional
    public PageDetail bulkConfirm(
            UUID shopId,
            UUID pageId,
            BulkConfirmRequest request,
            UserPrincipal principal) {
        NotebookPage page = pageRepository.findForUpdate(pageId, shopId)
                .orElseThrow(() -> ApiException.notFound("Page not found"));
        if (page.getStatus() != PageStatus.NEEDS_REVIEW) {
            throw ApiException.conflict("page_not_reviewable", "This page cannot be reviewed in its current state.");
        }
        ExtractionRun run = runRepository.findTopByPageIdOrderByCreatedAtDesc(pageId).orElse(null);
        List<ExtractedEntry> entries = run == null
                ? List.of()
                : entryRepository.findAllByRunIdOrderByEntryIndexAsc(run.getId());
        Map<UUID, ReviewedEntry> reviews = new HashMap<>();
        if (!entries.isEmpty()) {
            List<UUID> ids = entries.stream().map(ExtractedEntry::getId).toList();
            for (ReviewedEntry review : reviewedRepository.findAllForEntries(ids)) {
                reviews.put(review.getExtractedEntry().getId(), review);
            }
        }
        Customer requested = customer(request.customerId(), page);
        String requestedName = request.customerName() == null ? null : request.customerName().trim();
        List<Customer> customers = customerRepository.findAllByShopIdOrderByDisplayNameAsc(shopId);
        User reviewer = userRepository.getReferenceById(principal.id());
        Customer autoCustomer = null;
        boolean autoCustomerResolved = false;

        for (ExtractedEntry entry : entries) {
            ReviewedEntry review = reviews.get(entry.getId());
            if (review == null) {
                LocalDate date = entry.getEntryDate() != null ? entry.getEntryDate() : request.date();
                ValueStatus dateStatus = entry.getEntryDate() != null
                        ? entry.getDateStatus()
                        : (date != null ? ValueStatus.READABLE : entry.getDateStatus());
                if (!confirmable(entry, date)) {
                    continue;
                }
                Customer target = requested;
                if (target == null && requestedName != null && !requestedName.isEmpty()) {
                    if (!autoCustomerResolved) {
                        autoCustomer = ensureCustomer(page, requestedName, principal);
                        autoCustomerResolved = true;
                    }
                    target = autoCustomer;
                }
                if (target == null) {
                    target = customerMatcher.match(customers, customerNameFor(entry, run)).single();
                }
                if (target == null) {
                    continue;
                }
                review = new ReviewedEntry();
                review.setExtractedEntry(entry);
                review.setAction(ReviewAction.CONFIRMED);
                review.setFinalDate(date);
                review.setFinalDateStatus(dateStatus);
                review.setFinalDescription(entry.getDescription());
                review.setFinalDebitPaise(entry.getDebitPaise());
                review.setFinalCreditPaise(entry.getCreditPaise());
                review.setFinalEntryStatus(entry.getEntryStatus());
                review.setCustomer(target);
                review.setReviewer(reviewer);
                review.setDecidedAt(Instant.now());
                reviewedRepository.save(review);
                auditService.record(
                        page.getShop(),
                        reviewer,
                        "entry.reviewed",
                        "extracted_entry",
                        entry.getId(),
                        null,
                        after(review));
                continue;
            }
            if (review.getCustomer() != null || review.getAction() == ReviewAction.REJECTED) {
                continue;
            }
            Customer target = requested;
            if (target == null && requestedName != null && !requestedName.isEmpty()) {
                if (!autoCustomerResolved) {
                    autoCustomer = ensureCustomer(page, requestedName, principal);
                    autoCustomerResolved = true;
                }
                target = autoCustomer;
            }
            if (target == null) {
                target = customerMatcher.match(customers, customerNameFor(entry, run)).single();
            }
            if (target == null) {
                continue;
            }
            Map<String, Object> before = before(review);
            review.setCustomer(target);
            review.setDecidedAt(Instant.now());
            reviewedRepository.save(review);
            auditService.record(
                    page.getShop(),
                    reviewer,
                    "entry.reviewed",
                    "extracted_entry",
                    entry.getId(),
                    before,
                    after(review));
        }
        return pageViewService.detail(page);
    }

    private void applyCorrection(ReviewedEntry review, ExtractedEntry entry, ReviewRequest request) {
        if (request.date() == null) {
            throw ApiException.badRequest("missing_date", "Enter the corrected date.");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw ApiException.badRequest("missing_description", "Enter a description for the entry.");
        }
        Money.requireImportableAmount(request.debitPaise(), request.creditPaise());
        EntryStatus status = request.entryStatus() == null ? EntryStatus.ACTIVE : request.entryStatus();
        if (!status.importable()) {
            throw ApiException.badRequest("invalid_entry_status", "A corrected entry must remain importable.");
        }
        review.setFinalDate(request.date());
        review.setFinalDateStatus(request.dateStatus() == null ? ValueStatus.READABLE : request.dateStatus());
        review.setFinalDescription(request.description().trim());
        review.setFinalDebitPaise(request.debitPaise());
        review.setFinalCreditPaise(request.creditPaise());
        review.setFinalEntryStatus(status);
    }

    private void requireConfirmable(ExtractedEntry entry) {
        if (!entry.getEntryStatus().importable()) {
            throw ApiException.badRequest("entry_not_importable", "This entry cannot be confirmed as an import.");
        }
        if (entry.getEntryDate() == null) {
            throw ApiException.badRequest("missing_date", "Correct the date before confirming this entry.");
        }
        Money.requireImportableAmount(entry.getDebitPaise(), entry.getCreditPaise());
    }

    private static boolean confirmable(ExtractedEntry entry, LocalDate date) {
        if (!entry.getEntryStatus().importable() || date == null) {
            return false;
        }
        try {
            Money.requireImportableAmount(entry.getDebitPaise(), entry.getCreditPaise());
            return true;
        } catch (Money.InvalidMoneyException exception) {
            return false;
        }
    }

    private static String customerNameFor(ExtractedEntry entry, ExtractionRun run) {
        String name = readable(entry.getCustomerNameValue(), entry.getCustomerNameStatus());
        if (name != null) {
            return name;
        }
        return run == null ? null : readable(run.getPageCustomerName(), run.getPageCustomerStatus());
    }

    private Customer ensureCustomer(NotebookPage page, String name, UserPrincipal principal) {
        String displayName = name == null ? null : name.trim();
        if (displayName == null || displayName.isEmpty() || displayName.length() > 200) {
            return null;
        }
        String normalized = CustomerMatcher.normalise(displayName);
        if (normalized.isEmpty()) {
            return null;
        }
        Customer existing = customerRepository
                .findByShopIdAndNormalizedName(page.getShop().getId(), normalized)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        Customer customer = new Customer();
        customer.setShop(page.getShop());
        customer.setDisplayName(displayName);
        customer.setNormalizedName(normalized);
        customer.setAliases(List.of());
        customer = customerRepository.save(customer);
        auditService.record(
                page.getShop(),
                userRepository.findById(principal.id()).orElse(null),
                "customer.created",
                "customer",
                customer.getId(),
                null,
                Map.of("display_name", customer.getDisplayName(), "aliases", List.of()));
        return customer;
    }

    private Customer customer(UUID customerId, NotebookPage page) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findByIdAndShopId(customerId, page.getShop().getId())
                .orElseThrow(() -> ApiException.badRequest("invalid_customer", "Choose a customer from this shop."));
    }

    private static String readable(String value, ValueStatus status) {
        return status == ValueStatus.UNREADABLE || value == null || value.isBlank() ? null : value;
    }

    private static String note(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 2000 ? trimmed : trimmed.substring(0, 2000);
    }

    private static Customer firstNonNull(Customer... values) {
        for (Customer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Object> before(ReviewedEntry review) {
        if (review == null) {
            return null;
        }
        return reviewMap(review);
    }

    private Map<String, Object> after(ReviewedEntry review) {
        return reviewMap(review);
    }

    private Map<String, Object> reviewMap(ReviewedEntry review) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", review.getAction() == null ? null : review.getAction().wire());
        map.put("date", review.getFinalDate());
        map.put("description", review.getFinalDescription());
        map.put("debit_paise", review.getFinalDebitPaise());
        map.put("credit_paise", review.getFinalCreditPaise());
        map.put("entry_status", review.getFinalEntryStatus() == null ? null : review.getFinalEntryStatus().wire());
        map.put("customer_id", review.getCustomer() == null ? null : review.getCustomer().getId().toString());
        return map;
    }
}
