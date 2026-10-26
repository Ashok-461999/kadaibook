package com.kadaibook.service;

import com.kadaibook.domain.NotebookPage;
import com.kadaibook.repository.NotebookPageRepository;
import com.kadaibook.security.UserPrincipal;
import com.kadaibook.storage.StorageService;
import com.kadaibook.web.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PageImageService {

    private final AccessService accessService;
    private final NotebookPageRepository pageRepository;
    private final StorageService storageService;

    public PageImageService(
            AccessService accessService,
            NotebookPageRepository pageRepository,
            StorageService storageService) {
        this.accessService = accessService;
        this.pageRepository = pageRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public PageImage load(UUID shopId, UUID pageId, UserPrincipal principal) {
        accessService.requireShop(shopId, principal);
        NotebookPage page = pageRepository.findByIdAndShopId(pageId, shopId)
                .orElseThrow(() -> ApiException.notFound("Page not found"));
        if (page.getImageDeletedAt() != null) {
            throw ApiException.gone("image_deleted", "The page image has been deleted by the retention policy.");
        }
        byte[] bytes;
        try {
            bytes = storageService.get(page.getS3Key());
        } catch (RuntimeException exception) {
            throw ApiException.notFound("The stored page image could not be found.");
        }
        return new PageImage(bytes, page.getContentType(), page.getOriginalFilename());
    }

    public record PageImage(byte[] bytes, String contentType, String filename) {
    }
}
