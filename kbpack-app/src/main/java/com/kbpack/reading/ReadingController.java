package com.kbpack.reading;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.page.PageResponse;
import com.kbpack.pkg.PackageAccessService;
import com.kbpack.user.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reading")
public class ReadingController {

    public record ProgressRequest(
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progress
    ) {
    }

    public record BookmarkRequest(@NotNull Boolean bookmarked) {
    }

    private final ReadingService readingService;
    private final PackageAccessService accessService;

    public ReadingController(ReadingService readingService, PackageAccessService accessService) {
        this.readingService = readingService;
        this.accessService = accessService;
    }

    @GetMapping("/documents/{documentId}/state")
    public ReadingService.DocumentState state(@PathVariable String documentId) {
        AppUser user = accessService.currentUser();
        return readingService.state(parseDocumentId(documentId), user);
    }

    @PutMapping("/documents/{documentId}/progress")
    public ReadingService.DocumentState updateProgress(
            @PathVariable String documentId,
            @Valid @RequestBody ProgressRequest body
    ) {
        AppUser user = accessService.currentUser();
        return readingService.updateProgress(parseDocumentId(documentId), body.progress(), user);
    }

    @PutMapping("/documents/{documentId}/bookmark")
    public ReadingService.DocumentState updateBookmark(
            @PathVariable String documentId,
            @Valid @RequestBody BookmarkRequest body
    ) {
        AppUser user = accessService.currentUser();
        return readingService.setBookmarked(parseDocumentId(documentId), body.bookmarked(), user);
    }

    @GetMapping("/recent")
    public PageResponse<ReadingService.ReadingItem> recent(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        AppUser user = accessService.currentUser();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(pageSize, 1), 100);
        return readingService.recent(
                user,
                normalizedPage,
                normalizedSize,
                PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "viewedAt"))
        );
    }

    @GetMapping("/bookmarks")
    public PageResponse<ReadingService.ReadingItem> bookmarks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize
    ) {
        AppUser user = accessService.currentUser();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(pageSize, 1), 100);
        return readingService.bookmarks(
                user,
                normalizedPage,
                normalizedSize,
                PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    private UUID parseDocumentId(String externalId) {
        try {
            return IdPrefix.DOCUMENT.parse(externalId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Document not found");
        }
    }
}
