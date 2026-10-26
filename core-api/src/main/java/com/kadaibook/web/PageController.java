package com.kadaibook.web;

import com.kadaibook.security.UserPrincipal;
import com.kadaibook.service.AccessService;
import com.kadaibook.service.PageDeletionService;
import com.kadaibook.service.PageImageService;
import com.kadaibook.service.PageService;
import com.kadaibook.service.PageViewService;
import com.kadaibook.service.ReviewService;
import com.kadaibook.web.dto.PageDtos.BulkConfirmRequest;
import com.kadaibook.web.dto.PageDtos.ConfirmRequest;
import com.kadaibook.web.dto.PageDtos.PageDetail;
import com.kadaibook.web.dto.PageDtos.PageSummary;
import com.kadaibook.web.dto.PageDtos.ReviewRequest;
import com.kadaibook.web.dto.PageDtos.UploadResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/shops/{shopId}/pages")
public class PageController {

    private final AccessService accessService;
    private final PageService pageService;
    private final PageViewService pageViewService;
    private final PageImageService pageImageService;
    private final PageDeletionService pageDeletionService;
    private final ReviewService reviewService;

    public PageController(
            AccessService accessService,
            PageService pageService,
            PageViewService pageViewService,
            PageImageService pageImageService,
            PageDeletionService pageDeletionService,
            ReviewService reviewService) {
        this.accessService = accessService;
        this.pageService = pageService;
        this.pageViewService = pageViewService;
        this.pageImageService = pageImageService;
        this.pageDeletionService = pageDeletionService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<PageSummary> list(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return pageViewService.list(accessService.requireShop(shopId, principal));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @PathVariable UUID shopId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        UploadResponse response = pageService.upload(shopId, file, principal);
        return ResponseEntity.status(response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{pageId}")
    public PageDetail detail(
            @PathVariable UUID shopId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal UserPrincipal principal) {
        accessService.requireShop(shopId, principal);
        return pageViewService.detail(shopId, pageId);
    }

    @PostMapping("/{pageId}/extract")
    public PageDetail extract(
            @PathVariable UUID shopId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return pageService.extract(shopId, pageId, principal);
    }

    @GetMapping("/{pageId}/image")
    public ResponseEntity<byte[]> image(
            @PathVariable UUID shopId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageImageService.PageImage image = pageImageService.load(shopId, pageId, principal);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(image.filename() == null ? "page" : image.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    @PutMapping("/{pageId}/entries/{entryId}/review")
    public PageDetail review(
            @PathVariable UUID shopId,
            @PathVariable UUID pageId,
            @PathVariable UUID entryId,
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reviewService.review(shopId, pageId, entryId, request, principal);
    }

    @PostMapping("/{pageId}/confirm")
    public PageDetail confirm(
            @PathVariable UUID shopId,
            @PathVariable UUID pageId,
            @Valid @RequestBody ConfirmRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reviewService.confirm(shopId, pageId, request, principal);
    }

    @PostMapping("/{pageId}/bulk-confirm")
    public PageDetail bulkConfirm(
            @PathVariable UUID shopId,
            @PathVariable UUID pageId,
            @Valid @RequestBody BulkConfirmRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reviewService.bulkConfirm(shopId, pageId, request, principal);
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID shopId,
            @PathVariable UUID pageId,
            @AuthenticationPrincipal UserPrincipal principal) {
        pageDeletionService.delete(shopId, pageId, principal);
        return ResponseEntity.noContent().build();
    }
}
