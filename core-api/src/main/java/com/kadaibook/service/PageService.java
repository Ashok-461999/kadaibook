package com.kadaibook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kadaibook.ai.AiExtractionEnvelope;
import com.kadaibook.ai.AiServiceClient;
import com.kadaibook.ai.AiServiceException;
import com.kadaibook.ai.ExtractionContractValidator;
import com.kadaibook.ai.ExtractionPayload;
import com.kadaibook.domain.Customer;
import com.kadaibook.domain.EntryStatus;
import com.kadaibook.domain.ExtractedEntry;
import com.kadaibook.domain.ExtractionRun;
import com.kadaibook.domain.LayoutType;
import com.kadaibook.domain.NotebookPage;
import com.kadaibook.domain.PageStatus;
import com.kadaibook.domain.ReviewedEntry;
import com.kadaibook.domain.ReviewAction;
import com.kadaibook.domain.RunStatus;
import com.kadaibook.domain.Shop;
import com.kadaibook.domain.User;
import com.kadaibook.domain.ValueStatus;
import com.kadaibook.money.Money;
import com.kadaibook.repository.CustomerRepository;
import com.kadaibook.repository.ExtractedEntryRepository;
import com.kadaibook.repository.ExtractionRunRepository;
import com.kadaibook.repository.NotebookPageRepository;
import com.kadaibook.repository.ReviewedEntryRepository;
import com.kadaibook.repository.ShopRepository;
import com.kadaibook.repository.UserRepository;
import com.kadaibook.security.UserPrincipal;
import com.kadaibook.storage.StorageService;
import com.kadaibook.web.ApiException;
import com.kadaibook.web.dto.PageDtos.PageDetail;
import com.kadaibook.web.dto.PageDtos.PageSummary;
import com.kadaibook.web.dto.PageDtos.UploadResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PageService {

    private static final Logger log = LoggerFactory.getLogger(PageService.class);

    private final AccessService accessService;
    private final NotebookPageRepository pageRepository;
    private final ExtractionRunRepository runRepository;
    private final ExtractedEntryRepository entryRepository;
    private final ReviewedEntryRepository reviewedRepository;
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ImageValidator imageValidator;
    private final StorageService storageService;
    private final AiServiceClient aiServiceClient;
    private final ExtractionContractValidator contractValidator;
    private final CustomerMatcher customerMatcher;
    private final PageViewService pageViewService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PageService(
            AccessService accessService,
            NotebookPageRepository pageRepository,
            ExtractionRunRepository runRepository,
            ExtractedEntryRepository entryRepository,
            ReviewedEntryRepository reviewedRepository,
            CustomerRepository customerRepository,
            ShopRepository shopRepository,
            UserRepository userRepository,
            ImageValidator imageValidator,
            StorageService storageService,
            AiServiceClient aiServiceClient,
            ExtractionContractValidator contractValidator,
            CustomerMatcher customerMatcher,
            PageViewService pageViewService,
            AuditService auditService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate) {
        this.accessService = accessService;
        this.pageRepository = pageRepository;
        this.runRepository = runRepository;
        this.entryRepository = entryRepository;
        this.reviewedRepository = reviewedRepository;
        this.customerRepository = customerRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.imageValidator = imageValidator;
        this.storageService = storageService;
        this.aiServiceClient = aiServiceClient;
        this.contractValidator = contractValidator;
        this.customerMatcher = customerMatcher;
        this.pageViewService = pageViewService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public UploadResponse upload(UUID shopId, MultipartFile file, UserPrincipal principal) {
        Shop shop = accessService.requireShop(shopId, principal);
        User actor = userRepository.findById(principal.id()).orElseThrow(() -> ApiException.unauthorized("Sign in again."));
        ImageValidator.ValidatedImage image = imageValidator.validate(file);
        String hash = sha256(image.bytes());
        var duplicate = pageRepository.findByShopIdAndImageSha256(shop.getId(), hash);
        if (duplicate.isPresent()) {
            NotebookPage existing = duplicate.get();
            ExtractionRun run = runRepository.findTopByPageIdOrderByCreatedAtDesc(existing.getId()).orElse(null);
            return new UploadResponse(pageViewService.summary(existing, run), true);
        }
        String key = "shops/" + shop.getId() + "/pages/" + hash + image.extension();
        storageService.put(key, image.bytes(), image.contentType());

        NotebookPage page = new NotebookPage();
        page.setShop(shop);
        page.setS3Key(key);
        page.setImageSha256(hash);
        page.setOriginalFilename(safeFilename(file.getOriginalFilename(), image.extension()));
        page.setContentType(image.contentType());
        page.setSizeBytes(image.bytes().length);
        page.setStatus(PageStatus.UPLOADED);
        try {
            page = pageRepository.save(page);
        } catch (RuntimeException exception) {
            if (pageRepository.findByShopIdAndImageSha256(shop.getId(), hash).isEmpty()) {
                storageService.delete(key);
            }
            throw exception;
        }
        auditService.record(
                shop,
                actor,
                "page.uploaded",
                "page",
                page.getId(),
                null,
                Map.of(
                        "sha256", hash,
                        "bytes", image.bytes().length,
                        "width", image.width(),
                        "height", image.height()));
        return new UploadResponse(pageViewService.summary(page, null), false);
    }

    public PageDetail extract(UUID shopId, UUID pageId, UserPrincipal principal) {
        accessService.requireShop(shopId, principal);
        User actor = userRepository.findById(principal.id()).orElseThrow(() -> ApiException.unauthorized("Sign in again."));
        Begin begin = transactionTemplate.execute(status -> beginExtraction(shopId, pageId, actor.getId()));
        if (begin == null) {
            throw ApiException.conflict("extraction_not_started", "The extraction could not be started.");
        }
        try {
            byte[] image;
            try {
                image = storageService.get(begin.storageKey());
            } catch (RuntimeException exception) {
                log.error("page image unavailable for page {}", pageId, exception);
                fail(begin.runId(), RunStatus.TRANSPORT_ERROR, "The page image could not be read.", null);
                return pageViewService.detail(shopId, pageId);
            }

            AiExtractionEnvelope envelope;
            try {
                envelope = aiServiceClient.extract(
                        pageId, begin.runId(), image, begin.filename(), begin.contentType());
            } catch (AiServiceException exception) {
                RunStatus status = exception.kind() == AiServiceException.Kind.TRANSPORT
                        ? RunStatus.TRANSPORT_ERROR
                        : RunStatus.EXTRACTION_FAILED;
                log.warn("AI extraction failed for page {}: {}", pageId, exception.getMessage());
                fail(begin.runId(), status, safeMessage(exception.getMessage()), null);
                return pageViewService.detail(shopId, pageId);
            }

            if (!"ok".equals(envelope.status())) {
                RunStatus status = "unsupported_layout".equals(envelope.status())
                        ? RunStatus.UNSUPPORTED_LAYOUT
                        : RunStatus.EXTRACTION_FAILED;
                String message = envelope.error() == null || envelope.error().isBlank()
                        ? "The page could not be read."
                        : truncate(envelope.error(), 800);
                fail(begin.runId(), status, message, envelope);
                return pageViewService.detail(shopId, pageId);
            }

            JsonNode extraction = envelope.extraction();
            if (extraction == null) {
                fail(begin.runId(), RunStatus.EXTRACTION_FAILED, "The AI response had no extraction.", envelope);
                return pageViewService.detail(shopId, pageId);
            }
            ExtractionPayload payload;
            try {
                contractValidator.validate(extraction, pageId.toString());
                payload = objectMapper.treeToValue(extraction, ExtractionPayload.class);
                validatePayload(payload);
            } catch (ExtractionContractValidator.ExtractionContractException
                     | JsonProcessingException
                     | IllegalArgumentException exception) {
                log.warn("AI contract violation for page {}: {}", pageId, exception.getMessage());
                fail(begin.runId(), RunStatus.EXTRACTION_FAILED,
                        "The extraction did not pass validation: " + truncate(exception.getMessage(), 400),
                        envelope);
                return pageViewService.detail(shopId, pageId);
            }

            UUID runId = begin.runId();
            UUID actorId = principal.id();
            transactionTemplate.executeWithoutResult(
                    status -> completeSuccess(runId, payload, envelope, actorId));
            return pageViewService.detail(shopId, pageId);
        } catch (RuntimeException exception) {
            log.error("unexpected extraction failure for page {}", pageId, exception);
            fail(begin.runId(), RunStatus.TRANSPORT_ERROR, "Extraction could not be completed.", null);
            return pageViewService.detail(shopId, pageId);
        }
    }

    private Begin beginExtraction(UUID shopId, UUID pageId, UUID actorId) {
        NotebookPage page = pageRepository.findForUpdate(pageId, shopId)
                .orElseThrow(() -> ApiException.notFound("Page not found"));
        if (page.getStatus() != PageStatus.UPLOADED && page.getStatus() != PageStatus.EXTRACTION_FAILED) {
            throw ApiException.conflict("page_not_ready", "This page cannot be extracted in its current state.");
        }
        if (page.getImageDeletedAt() != null) {
            throw ApiException.gone("image_deleted", "The page image has been deleted and cannot be extracted.");
        }
        page.setStatus(PageStatus.EXTRACTING);
        ExtractionRun run = new ExtractionRun();
        run.setPage(page);
        run.setStatus(RunStatus.RUNNING);
        run.setAttempts(0);
        run.setPageIssues(new ArrayList<>());
        run = runRepository.save(run);
        User actor = userRepository.getReferenceById(actorId);
        auditService.record(
                page.getShop(),
                actor,
                "extraction.started",
                "page",
                page.getId(),
                Map.of("status", PageStatus.UPLOADED.wire()),
                Map.of("status", PageStatus.EXTRACTING.wire(), "run_id", run.getId().toString()));
        return new Begin(run.getId(), page.getS3Key(), page.getOriginalFilename(), page.getContentType());
    }

    private void completeSuccess(
            UUID runId,
            ExtractionPayload payload,
            AiExtractionEnvelope envelope,
            UUID actorId) {
        ExtractionRun run = runRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Extraction run not found"));
        NotebookPage page = pageRepository.findForUpdate(run.getPage().getId(), run.getPage().getShop().getId())
                .orElseThrow(() -> ApiException.notFound("Page not found"));
        if (run.getStatus() != RunStatus.RUNNING) {
            throw ApiException.conflict("run_not_running", "This extraction run is no longer active.");
        }
        LayoutType layout = LayoutType.from(payload.layoutType());
        run.setStatus(RunStatus.SUCCESS);
        run.setModel(truncate(envelope.model(), 160));
        run.setPromptVersion(truncate(envelope.promptVersion(), 60));
        run.setSchemaVersion("1.0");
        run.setRawJson(rawJson(envelope));
        run.setAttempts(Math.max(0, envelope.attempts()));
        run.setLatencyMs(Math.max(0, envelope.latencyMs()));
        run.setCostPaise(Math.max(0, envelope.costPaise()));
        run.setInputTokens(Math.max(0, envelope.inputTokens()));
        run.setOutputTokens(Math.max(0, envelope.outputTokens()));
        run.setLayoutConfidence(envelope.layoutConfidence());
        run.setOpeningBalancePaise(payload.openingBalancePaise());
        run.setClosingBalancePaise(payload.closingBalancePaise());
        if (payload.customerName() != null) {
            run.setPageCustomerName(trimToNull(payload.customerName().value(), 200));
            run.setPageCustomerStatus(ValueStatus.from(payload.customerName().status()));
        }
        run.setPageIssues(payload.pageIssues() == null ? new ArrayList<>() : new ArrayList<>(payload.pageIssues()));
        run.setReviewRequired(payload.reviewRequired());

        List<Customer> customers = customerRepository.findAllByShopIdOrderByDisplayNameAsc(page.getShop().getId());
        List<ExtractedEntry> entries = persistEntries(run, payload, customers);
        int autoReviewed = autoReview(entries, run, page, customers, actorId);
        run.setReviewRequired(payload.reviewRequired() || entries.stream().anyMatch(entry -> !isClean(entry, run, customers)));
        page.setLayoutType(layout);
        page.setStatus(PageStatus.NEEDS_REVIEW);
        User actor = userRepository.getReferenceById(actorId);
        auditService.record(
                page.getShop(),
                actor,
                "extraction.completed",
                "page",
                page.getId(),
                Map.of("status", PageStatus.EXTRACTING.wire()),
                Map.of(
                        "status", PageStatus.NEEDS_REVIEW.wire(),
                        "run_id", run.getId().toString(),
                        "entries", entries.size(),
                        "auto_reviewed", autoReviewed,
                        "model", run.getModel() == null ? "unknown" : run.getModel()));
    }

    private List<ExtractedEntry> persistEntries(
            ExtractionRun run,
            ExtractionPayload payload,
            List<Customer> customers) {
        List<ExtractionPayload.Entry> source = payload.entries() == null ? List.of() : payload.entries();
        List<ExtractedEntry> entries = new ArrayList<>();
        int index = 0;
        for (ExtractionPayload.Entry item : source) {
            ExtractedEntry entry = new ExtractedEntry();
            entry.setRun(run);
            entry.setEntryIndex(index++);
            entry.setEntryDate(item.date());
            entry.setDateStatus(ValueStatus.from(item.dateStatus()));
            entry.setDescription(trimToNull(item.description(), 4000));
            entry.setDebitPaise(item.debitPaise());
            entry.setCreditPaise(item.creditPaise());
            entry.setEntryStatus(EntryStatus.from(item.entryStatus()));
            entry.setIssues(item.issues() == null ? new ArrayList<>() : new ArrayList<>(item.issues()));
            entry.setSourceText(trimToNull(item.sourceText(), 4000));
            entry.setRunningBalancePaise(item.runningBalancePaise());
            if (item.customerName() != null) {
                entry.setCustomerNameValue(trimToNull(item.customerName().value(), 200));
                entry.setCustomerNameStatus(ValueStatus.from(item.customerName().status()));
            }
            entries.add(entry);
        }
        return entryRepository.saveAll(entries);
    }

    private int autoReview(
            List<ExtractedEntry> entries,
            ExtractionRun run,
            NotebookPage page,
            List<Customer> customers,
            UUID actorId) {
        if (!run.getPageIssues().isEmpty()) {
            return 0;
        }
        User actor = userRepository.getReferenceById(actorId);
        int reviewed = 0;
        for (ExtractedEntry entry : entries) {
            if (!isClean(entry, run, customers)) {
                continue;
            }
            String name = readable(entry.getCustomerNameValue(), entry.getCustomerNameStatus());
            if (name == null) {
                name = readable(run.getPageCustomerName(), run.getPageCustomerStatus());
            }
            CustomerMatcher.Match match = customerMatcher.match(customers, name);
            if (match.status() != com.kadaibook.domain.MatchStatus.MATCHED) {
                continue;
            }
            ReviewedEntry review = new ReviewedEntry();
            review.setExtractedEntry(entry);
            review.setReviewer(actor);
            review.setAction(ReviewAction.CONFIRMED);
            review.setFinalDate(entry.getEntryDate());
            review.setFinalDateStatus(entry.getDateStatus());
            review.setFinalDescription(entry.getDescription());
            review.setFinalDebitPaise(entry.getDebitPaise());
            review.setFinalCreditPaise(entry.getCreditPaise());
            review.setFinalEntryStatus(entry.getEntryStatus());
            review.setCustomer(match.single());
            review.setNote("Auto-confirmed: readable fields, matched customer, and a valid amount.");
            reviewedRepository.save(review);
            reviewed++;
        }
        if (reviewed > 0) {
            auditService.record(
                    page.getShop(),
                    actor,
                    "extraction.auto_reviewed",
                    "page",
                    page.getId(),
                    null,
                    Map.of("entries", reviewed));
        }
        return reviewed;
    }

    private boolean isClean(ExtractedEntry entry, ExtractionRun run, List<Customer> customers) {
        if (entry.getEntryStatus() != EntryStatus.ACTIVE
                || entry.getDateStatus() != ValueStatus.READABLE
                || entry.getEntryDate() == null
                || entry.getIssues() != null && !entry.getIssues().isEmpty()) {
            return false;
        }
        try {
            Money.requireImportableAmount(entry.getDebitPaise(), entry.getCreditPaise());
        } catch (Money.InvalidMoneyException exception) {
            return false;
        }
        String name = readable(entry.getCustomerNameValue(), entry.getCustomerNameStatus());
        if (name == null) {
            name = readable(run.getPageCustomerName(), run.getPageCustomerStatus());
        }
        return customerMatcher.match(customers, name).status() == com.kadaibook.domain.MatchStatus.MATCHED;
    }

    private void validatePayload(ExtractionPayload payload) {
        if (payload.entries() != null && payload.entries().size() > 500) {
            throw new IllegalArgumentException("too many entries");
        }
        if (!Money.isValidSigned(payload.openingBalancePaise())
                || !Money.isValidSigned(payload.closingBalancePaise())) {
            throw new IllegalArgumentException("balance is outside the supported range");
        }
        for (ExtractionPayload.Entry entry : payload.entries() == null ? List.<ExtractionPayload.Entry>of() : payload.entries()) {
            if (!Money.isValidAmount(entry.debitPaise()) && !Money.isValidAmount(entry.creditPaise())) {
                if (entry.debitPaise() != null || entry.creditPaise() != null) {
                    throw new IllegalArgumentException("entry amount is outside the supported range");
                }
            }
        }
    }

    private void fail(UUID runId, RunStatus status, String message, AiExtractionEnvelope envelope) {
        try {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                ExtractionRun run = runRepository.findById(runId).orElse(null);
                if (run == null || run.getStatus() != RunStatus.RUNNING) {
                    return;
                }
                run.setStatus(status);
                run.setError(truncate(message, 800));
                if (envelope != null) {
                    run.setModel(truncate(envelope.model(), 160));
                    run.setPromptVersion(truncate(envelope.promptVersion(), 60));
                    run.setSchemaVersion("1.0");
                    run.setRawJson(rawJson(envelope));
                    run.setAttempts(Math.max(0, envelope.attempts()));
                    run.setLatencyMs(Math.max(0, envelope.latencyMs()));
                    run.setCostPaise(Math.max(0, envelope.costPaise()));
                    run.setInputTokens(Math.max(0, envelope.inputTokens()));
                    run.setOutputTokens(Math.max(0, envelope.outputTokens()));
                    run.setLayoutConfidence(envelope.layoutConfidence());
                    if (envelope.layoutType() != null && !envelope.layoutType().isBlank()) {
                        run.getPage().setLayoutType(LayoutType.from(envelope.layoutType()));
                    }
                }
                NotebookPage page = run.getPage();
                page.setStatus(PageStatus.EXTRACTION_FAILED);
                pageRepository.save(page);
                auditService.record(
                        page.getShop(),
                        null,
                        "extraction.failed",
                        "page",
                        page.getId(),
                        Map.of("status", PageStatus.EXTRACTING.wire()),
                        Map.of("status", PageStatus.EXTRACTION_FAILED.wire(),
                                "run_id", run.getId().toString(),
                                "reason", status.wire()));
            });
        } catch (RuntimeException exception) {
            log.error("could not record extraction failure for run {}", runId, exception);
        }
    }

    private String rawJson(AiExtractionEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static String readable(String value, ValueStatus status) {
        return status == ValueStatus.UNREADABLE || value == null || value.isBlank() ? null : value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safeFilename(String name, String extension) {
        String cleaned = name == null ? "page" + extension : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (cleaned.isEmpty()) {
            cleaned = "page" + extension;
        }
        return truncate(cleaned, 255);
    }

    private static String trimToNull(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return truncate(trimmed, max);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String safeMessage(String value) {
        return value == null || value.isBlank() ? "The AI service could not read the page." : truncate(value, 500);
    }

    private record Begin(UUID runId, String storageKey, String filename, String contentType) {
    }
}
