package com.kadaibook.service;

import com.kadaibook.domain.BalanceConfirmation;
import com.kadaibook.domain.ExtractedEntry;
import com.kadaibook.domain.ExtractionRun;
import com.kadaibook.domain.NotebookPage;
import com.kadaibook.domain.PageStatus;
import com.kadaibook.domain.ReviewedEntry;
import com.kadaibook.domain.Shop;
import com.kadaibook.repository.ExtractedEntryRepository;
import com.kadaibook.repository.ExtractionRunRepository;
import com.kadaibook.repository.NotebookPageRepository;
import com.kadaibook.storage.StorageService;
import com.kadaibook.web.dto.PageDtos.Blocker;
import com.kadaibook.web.dto.PageDtos.ConfirmationView;
import com.kadaibook.web.dto.PageDtos.CustomerBalancePreview;
import com.kadaibook.web.dto.PageDtos.CustomerMatch;
import com.kadaibook.web.dto.PageDtos.CustomerRef;
import com.kadaibook.web.dto.PageDtos.PageDetail;
import com.kadaibook.web.dto.PageDtos.PageEntry;
import com.kadaibook.web.dto.PageDtos.PageSummary;
import com.kadaibook.web.dto.PageDtos.ReviewView;
import com.kadaibook.web.dto.PageDtos.RunInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PageViewService {

    private final NotebookPageRepository pageRepository;
    private final ExtractionRunRepository runRepository;
    private final ExtractedEntryRepository entryRepository;
    private final ReconciliationLoader reconciliationLoader;
    private final StorageService storageService;

    public PageViewService(
            NotebookPageRepository pageRepository,
            ExtractionRunRepository runRepository,
            ExtractedEntryRepository entryRepository,
            ReconciliationLoader reconciliationLoader,
            StorageService storageService) {
        this.pageRepository = pageRepository;
        this.runRepository = runRepository;
        this.entryRepository = entryRepository;
        this.reconciliationLoader = reconciliationLoader;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<PageSummary> list(Shop shop) {
        List<PageSummary> summaries = new ArrayList<>();
        for (NotebookPage page : pageRepository.findAllByShopIdOrderByCreatedAtDesc(shop.getId())) {
            ExtractionRun run = runRepository.findTopByPageIdOrderByCreatedAtDesc(page.getId()).orElse(null);
            summaries.add(summary(page, run));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public PageDetail detail(UUID shopId, UUID pageId) {
        NotebookPage page = pageRepository.findByIdAndShopId(pageId, shopId)
                .orElseThrow(() -> com.kadaibook.web.ApiException.notFound("Page not found"));
        return detail(page);
    }

    @Transactional(readOnly = true)
    public PageDetail detail(NotebookPage page) {
        ReconciliationLoader.Loaded loaded = reconciliationLoader.load(page);
        return detail(page, loaded);
    }

    public PageSummary summary(NotebookPage page, ExtractionRun run) {
        boolean reviewRequired = run != null && run.isReviewRequired();
        int entryCount = 0;
        int flagged = 0;
        if (run != null) {
            List<ExtractedEntry> entries = entryRepository.findAllByRunIdOrderByEntryIndexAsc(run.getId());
            entryCount = entries.size();
            flagged = (int) entries.stream().filter(this::flagged).count();
        }
        return new PageSummary(
                page.getId(),
                page.getStatus(),
                page.getLayoutType(),
                page.getOriginalFilename(),
                page.getCreatedAt(),
                page.getImportedAt(),
                run == null ? null : run.getPageCustomerName(),
                reviewRequired,
                entryCount,
                flagged,
                run == null ? null : run.getError());
    }

    public PageDetail detail(NotebookPage page, ReconciliationLoader.Loaded loaded) {
        ExtractionRun run = loaded.run();
        ReconciliationEngine.Plan plan = loaded.plan();
        Map<UUID, BalanceConfirmation> confirmations = new HashMap<>();
        loaded.confirmations().forEach(confirmation ->
                confirmations.put(confirmation.getCustomer().getId(), confirmation));

        List<PageEntry> viewEntries = plan.rows().stream().map(this::entryView).toList();
        List<CustomerBalancePreview> previews = plan.previews().stream()
                .map(preview -> new CustomerBalancePreview(
                        preview.customer().getId(),
                        preview.customer().getDisplayName(),
                        preview.entryCount(),
                        preview.existingBalancePaise(),
                        preview.openingImported(),
                        preview.openingBalancePaise(),
                        preview.calculatedBalancePaise(),
                        preview.writtenClosingBalancePaise(),
                        preview.closingMismatch(),
                        preview.confirmedBalancePaise()))
                .toList();
        List<Blocker> blockers = plan.allBlockers().stream()
                .map(blocker -> new Blocker(blocker.code(), blocker.message(), blocker.entryId(), blocker.entryIndex()))
                .toList();
        List<ConfirmationView> confirmationViews = plan.previews().stream()
                .filter(preview -> preview.confirmedBalancePaise() != null)
                .map(preview -> {
                    BalanceConfirmation confirmation = confirmations.get(preview.customer().getId());
                    return new ConfirmationView(
                            preview.customer().getId(),
                            preview.customer().getDisplayName(),
                            preview.confirmedBalancePaise(),
                            preview.calculatedBalancePaise(),
                            confirmation == null ? null : confirmation.getConfirmedAt());
                })
                .toList();
        boolean imageAvailable = page.getImageDeletedAt() == null && imageExists(page.getS3Key());
        boolean canExtract = (page.getStatus() == PageStatus.UPLOADED
                || page.getStatus() == PageStatus.EXTRACTION_FAILED)
                && imageAvailable;
        return new PageDetail(
                page.getId(),
                page.getShop().getId(),
                page.getStatus(),
                page.getLayoutType(),
                page.getOriginalFilename(),
                page.getContentType(),
                page.getSizeBytes(),
                page.getCreatedAt(),
                page.getImportedAt(),
                "/api/v1/shops/" + page.getShop().getId() + "/pages/" + page.getId() + "/image",
                imageAvailable,
                run == null ? null : run.getPageCustomerName(),
                run == null ? null : run.getPageCustomerStatus(),
                run == null ? null : run.getOpeningBalancePaise(),
                run == null ? null : run.getClosingBalancePaise(),
                run == null ? List.of() : List.copyOf(run.getPageIssues()),
                run != null && run.isReviewRequired(),
                runInfo(run),
                viewEntries,
                previews,
                blockers,
                plan.warnings(),
                canExtract,
                page.getStatus() == PageStatus.NEEDS_REVIEW,
                plan.canConfirm(),
                confirmationViews);
    }

    public ReviewView reviewView(ReviewedEntry review, ExtractedEntry entry) {
        boolean edited = !equals(review.getFinalDate(), entry.getEntryDate())
                || !equals(review.getFinalDescription(), entry.getDescription())
                || !equals(review.getFinalDebitPaise(), entry.getDebitPaise())
                || !equals(review.getFinalCreditPaise(), entry.getCreditPaise())
                || review.getFinalEntryStatus() != entry.getEntryStatus();
        return new ReviewView(
                review.getId(),
                review.getAction(),
                review.getFinalDate(),
                review.getFinalDateStatus(),
                review.getFinalDescription(),
                review.getFinalDebitPaise(),
                review.getFinalCreditPaise(),
                review.getFinalEntryStatus(),
                review.getCustomer() == null ? null : review.getCustomer().getId(),
                review.getCustomer() == null ? null : review.getCustomer().getDisplayName(),
                review.getNote(),
                review.getDecidedAt(),
                edited);
    }

    private PageEntry entryView(ReconciliationEngine.Row row) {
        ExtractedEntry entry = row.entry();
        ReviewView reviewView = row.review() == null ? null : reviewView(row.review(), entry);
        CustomerMatch match = new CustomerMatch(
                row.matchStatus(),
                row.candidates().stream()
                        .map(customer -> new CustomerRef(customer.getId(), customer.getDisplayName()))
                        .toList());
        return new PageEntry(
                entry.getId(),
                entry.getEntryIndex(),
                entry.getEntryDate(),
                entry.getDateStatus(),
                entry.getDescription(),
                entry.getDebitPaise(),
                entry.getCreditPaise(),
                entry.getEntryStatus(),
                entry.getIssues() == null ? List.of() : List.copyOf(entry.getIssues()),
                entry.getSourceText(),
                entry.getRunningBalancePaise(),
                row.customerName(),
                entry.getCustomerNameStatus(),
                match,
                reviewView,
                row.date(),
                row.dateStatus(),
                row.description(),
                row.debitPaise(),
                row.creditPaise(),
                row.entryStatus(),
                row.customer() == null ? null : row.customer().getId(),
                row.customerName(),
                row.calculatedBalancePaise(),
                row.balanceMismatch(),
                row.importable());
    }

    private RunInfo runInfo(ExtractionRun run) {
        if (run == null) {
            return null;
        }
        return new RunInfo(
                run.getId(),
                run.getStatus(),
                run.getModel(),
                run.getPromptVersion(),
                run.getSchemaVersion(),
                run.getAttempts(),
                run.getLatencyMs(),
                run.getCostPaise(),
                run.getInputTokens(),
                run.getOutputTokens(),
                run.getLayoutConfidence(),
                run.getError(),
                run.getCreatedAt());
    }

    private boolean imageExists(String key) {
        try {
            return storageService.exists(key);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean flagged(ExtractedEntry entry) {
        return entry.getIssues() != null && !entry.getIssues().isEmpty()
                || entry.getEntryStatus() != com.kadaibook.domain.EntryStatus.ACTIVE
                || entry.getDateStatus() != com.kadaibook.domain.ValueStatus.READABLE
                || entry.getCustomerNameStatus() == com.kadaibook.domain.ValueStatus.UNREADABLE;
    }

    private static boolean equals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
