package com.kadaibook.service;

import com.kadaibook.domain.NotebookPage;
import com.kadaibook.domain.PageStatus;
import com.kadaibook.domain.Shop;
import com.kadaibook.repository.BalanceConfirmationRepository;
import com.kadaibook.repository.ExtractedEntryRepository;
import com.kadaibook.repository.ExtractionRunRepository;
import com.kadaibook.repository.NotebookPageRepository;
import com.kadaibook.repository.ReviewedEntryRepository;
import com.kadaibook.repository.UserRepository;
import com.kadaibook.security.UserPrincipal;
import com.kadaibook.storage.StorageService;
import com.kadaibook.web.ApiException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PageDeletionService {

    private static final Logger log = LoggerFactory.getLogger(PageDeletionService.class);

    private final NotebookPageRepository pageRepository;
    private final ExtractionRunRepository runRepository;
    private final ExtractedEntryRepository entryRepository;
    private final ReviewedEntryRepository reviewedRepository;
    private final BalanceConfirmationRepository confirmationRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public PageDeletionService(
            NotebookPageRepository pageRepository,
            ExtractionRunRepository runRepository,
            ExtractedEntryRepository entryRepository,
            ReviewedEntryRepository reviewedRepository,
            BalanceConfirmationRepository confirmationRepository,
            UserRepository userRepository,
            StorageService storageService,
            AuditService auditService) {
        this.pageRepository = pageRepository;
        this.runRepository = runRepository;
        this.entryRepository = entryRepository;
        this.reviewedRepository = reviewedRepository;
        this.confirmationRepository = confirmationRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Transactional
    public void delete(UUID shopId, UUID pageId, UserPrincipal principal) {
        NotebookPage page = pageRepository.findForUpdate(pageId, shopId)
                .orElseThrow(() -> ApiException.notFound("Page not found"));
        if (page.getStatus() == PageStatus.IMPORTED) {
            throw ApiException.conflict("page_imported", "An imported page cannot be deleted.");
        }
        Shop shop = page.getShop();
        String storageKey = page.getS3Key();
        PageStatus status = page.getStatus();
        reviewedRepository.deleteForPage(pageId);
        confirmationRepository.deleteAllByPageId(pageId);
        entryRepository.deleteAllByRunPageId(pageId);
        runRepository.deleteAllByPageId(pageId);
        pageRepository.delete(page);
        pageRepository.flush();
        auditService.record(
                shop,
                userRepository.getReferenceById(principal.id()),
                "page.deleted",
                "page",
                pageId,
                Map.of("status", status.wire()),
                null);
        deleteAfterCommit(storageKey);
    }

    private void deleteAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.delete(key);
                } catch (RuntimeException exception) {
                    log.warn("could not delete stored image {}", key, exception);
                }
            }
        });
    }
}
