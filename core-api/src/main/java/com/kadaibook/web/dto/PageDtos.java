package com.kadaibook.web.dto;

import com.kadaibook.domain.EntryStatus;
import com.kadaibook.domain.LayoutType;
import com.kadaibook.domain.MatchStatus;
import com.kadaibook.domain.PageStatus;
import com.kadaibook.domain.ReviewAction;
import com.kadaibook.domain.RunStatus;
import com.kadaibook.domain.ValueStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PageDtos {

    private PageDtos() {
    }

    public record UploadResponse(PageSummary page, boolean duplicate) {
    }

    public record PageSummary(
            UUID id,
            PageStatus status,
            LayoutType layoutType,
            String originalFilename,
            Instant createdAt,
            Instant importedAt,
            String customerName,
            boolean reviewRequired,
            int entryCount,
            int flaggedEntryCount,
            String error
    ) {
    }

    public record CustomerRef(UUID id, String displayName) {
    }

    public record CustomerMatch(MatchStatus status, List<CustomerRef> candidates) {
    }

    public record ReviewView(
            UUID id,
            ReviewAction action,
            LocalDate date,
            ValueStatus dateStatus,
            String description,
            Long debitPaise,
            Long creditPaise,
            EntryStatus entryStatus,
            UUID customerId,
            String customerName,
            String note,
            Instant decidedAt,
            boolean edited
    ) {
    }

    public record PageEntry(
            UUID id,
            int index,
            LocalDate date,
            ValueStatus dateStatus,
            String description,
            Long debitPaise,
            Long creditPaise,
            EntryStatus entryStatus,
            List<String> issues,
            String sourceText,
            Long runningBalancePaise,
            String customerName,
            ValueStatus customerNameStatus,
            CustomerMatch customerMatch,
            ReviewView review,
            LocalDate effectiveDate,
            ValueStatus effectiveDateStatus,
            String effectiveDescription,
            Long effectiveDebitPaise,
            Long effectiveCreditPaise,
            EntryStatus effectiveEntryStatus,
            UUID effectiveCustomerId,
            String effectiveCustomerName,
            Long calculatedBalancePaise,
            boolean balanceMismatch,
            boolean importable
    ) {
    }

    public record Blocker(String code, String message, UUID entryId, Integer entryIndex) {
    }

    public record CustomerBalancePreview(
            UUID customerId,
            String displayName,
            int entryCount,
            long existingBalancePaise,
            boolean openingImported,
            Long openingBalancePaise,
            long calculatedBalancePaise,
            Long writtenClosingBalancePaise,
            boolean closingMismatch,
            Long confirmedBalancePaise
    ) {
    }

    public record ConfirmationView(
            UUID customerId,
            String customerName,
            long confirmedBalancePaise,
            long calculatedBalancePaise,
            Instant confirmedAt
    ) {
    }

    public record RunInfo(
            UUID id,
            RunStatus status,
            String model,
            String promptVersion,
            String schemaVersion,
            int attempts,
            long latencyMs,
            long costPaise,
            long inputTokens,
            long outputTokens,
            Double layoutConfidence,
            String error,
            Instant createdAt
    ) {
    }

    public record PageDetail(
            UUID id,
            UUID shopId,
            PageStatus status,
            LayoutType layoutType,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Instant createdAt,
            Instant importedAt,
            String imageUrl,
            boolean imageAvailable,
            String customerName,
            ValueStatus customerStatus,
            Long openingBalancePaise,
            Long closingBalancePaise,
            List<String> pageIssues,
            boolean reviewRequired,
            RunInfo run,
            List<PageEntry> entries,
            List<CustomerBalancePreview> customerPreviews,
            List<Blocker> blockers,
            List<String> warnings,
            boolean canExtract,
            boolean canReview,
            boolean canConfirm,
            List<ConfirmationView> confirmations
    ) {
    }

    public record ReviewRequest(
            @NotNull ReviewAction action,
            LocalDate date,
            ValueStatus dateStatus,
            String description,
            Long debitPaise,
            Long creditPaise,
            EntryStatus entryStatus,
            UUID customerId,
            String note
    ) {
    }

    public record BalanceConfirmationInput(
            @NotNull UUID customerId,
            @NotNull Long confirmedBalancePaise
    ) {
    }

    public record ConfirmRequest(
            @NotEmpty @Valid List<BalanceConfirmationInput> confirmations
    ) {
    }

    public record BulkConfirmRequest(
            UUID customerId,
            @Size(max = 200) String customerName,
            LocalDate date
    ) {
    }
}
