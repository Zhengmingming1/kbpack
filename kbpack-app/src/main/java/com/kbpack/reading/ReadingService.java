package com.kbpack.reading;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.page.PageResponse;
import com.kbpack.parser.ExtractedDocument;
import com.kbpack.parser.ExtractedDocumentRepository;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAccessService;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.user.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReadingService {

    private static final String DEFAULT_BOOKMARK_ANCHOR = "";

    public record DocumentState(
            String document_id,
            BigDecimal progress,
            boolean is_bookmarked,
            Instant updated_at
    ) {
    }

    public record ReadingItem(
            String document_id,
            String document_title,
            String package_id,
            String package_title,
            String version_id,
            BigDecimal progress,
            boolean is_bookmarked,
            Instant last_read_at,
            Instant updated_at
    ) {
    }

    private final ReadingProgressRepository progressRepository;
    private final RecentViewRepository recentViewRepository;
    private final ReadingBookmarkRepository bookmarkRepository;
    private final ExtractedDocumentRepository documentRepository;
    private final KnowledgePackageRepository packageRepository;
    private final PackageVersionRepository versionRepository;
    private final PackageAccessService accessService;
    private final Clock clock;

    @Autowired
    public ReadingService(
            ReadingProgressRepository progressRepository,
            RecentViewRepository recentViewRepository,
            ReadingBookmarkRepository bookmarkRepository,
            ExtractedDocumentRepository documentRepository,
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            PackageAccessService accessService
    ) {
        this(progressRepository, recentViewRepository, bookmarkRepository, documentRepository,
                packageRepository, versionRepository, accessService, Clock.systemUTC());
    }

    ReadingService(
            ReadingProgressRepository progressRepository,
            RecentViewRepository recentViewRepository,
            ReadingBookmarkRepository bookmarkRepository,
            ExtractedDocumentRepository documentRepository,
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            PackageAccessService accessService,
            Clock clock
    ) {
        this.progressRepository = progressRepository;
        this.recentViewRepository = recentViewRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.documentRepository = documentRepository;
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.accessService = accessService;
        this.clock = clock;
    }

    @Transactional
    public DocumentState state(UUID documentId, AppUser user) {
        DocumentContext context = requireReadableDocument(documentId, user);
        recordRecent(context, user.getId(), documentId, clock.instant());
        return stateView(documentId, user.getId());
    }

    @Transactional
    public DocumentState updateProgress(UUID documentId, BigDecimal progress, AppUser user) {
        if (progress == null || progress.compareTo(BigDecimal.ZERO) < 0
                || progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Progress must be between 0 and 100");
        }
        DocumentContext context = requireReadableDocument(documentId, user);
        Instant now = clock.instant();
        progressRepository.upsert(
                user.getId(),
                documentId,
                context.document().getPackageId(),
                context.document().getVersionId(),
                null,
                progress,
                0,
                now
        );

        recordRecent(context, user.getId(), documentId, now);
        return stateView(documentId, user.getId());
    }

    @Transactional
    public DocumentState setBookmarked(UUID documentId, boolean bookmarked, AppUser user) {
        DocumentContext context = requireReadableDocument(documentId, user);
        if (bookmarked) {
            if (!bookmarkRepository.existsByUserIdAndDocumentIdAndAnchor(
                    user.getId(), documentId, DEFAULT_BOOKMARK_ANCHOR)) {
                bookmarkRepository.save(new ReadingBookmark(
                        user.getId(),
                        context.document().getPackageId(),
                        context.document().getVersionId(),
                        documentId,
                        DEFAULT_BOOKMARK_ANCHOR,
                        null,
                        null,
                        clock.instant()
                ));
            }
        } else {
            bookmarkRepository.deleteByUserIdAndDocumentIdAndAnchor(
                    user.getId(), documentId, DEFAULT_BOOKMARK_ANCHOR
            );
        }
        return stateView(documentId, user.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadingItem> recent(AppUser user, int page, int pageSize, Pageable pageable) {
        Page<RecentView> result = recentViewRepository.findAccessible(
                user.getId(), accessService.isAdministrator(user), pageable
        );
        return PageResponse.of(
                result.getTotalElements(),
                page,
                pageSize,
                result.getContent().stream().map(view -> recentItem(view, user.getId())).toList()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ReadingItem> bookmarks(AppUser user, int page, int pageSize, Pageable pageable) {
        Page<ReadingBookmark> result = bookmarkRepository.findAccessible(
                user.getId(), null, accessService.isAdministrator(user), pageable
        );
        return PageResponse.of(
                result.getTotalElements(),
                page,
                pageSize,
                result.getContent().stream().map(bookmark -> bookmarkItem(bookmark, user.getId())).toList()
        );
    }

    private DocumentContext requireReadableDocument(UUID documentId, AppUser user) {
        ExtractedDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Document not found"));
        KnowledgePackage pkg = accessService.requireReadable(document.getPackageId(), user);
        versionRepository.findActiveByIdAndPackageId(document.getVersionId(), document.getPackageId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Document not found"));
        return new DocumentContext(document, pkg);
    }

    private void recordRecent(DocumentContext context, UUID userId, UUID documentId, Instant viewedAt) {
        recentViewRepository.upsert(
                userId,
                documentId,
                context.document().getPackageId(),
                context.document().getVersionId(),
                viewedAt
        );
    }

    private DocumentState stateView(UUID documentId, UUID userId) {
        Optional<ReadingProgress> progress = progressRepository.findById(new ReadingProgressId(userId, documentId));
        Optional<ReadingBookmark> bookmark = bookmarkRepository.findByUserIdAndDocumentIdAndAnchor(
                userId, documentId, DEFAULT_BOOKMARK_ANCHOR
        );
        Instant progressUpdatedAt = progress.map(ReadingProgress::getUpdatedAt).orElse(null);
        Instant bookmarkUpdatedAt = bookmark.map(ReadingBookmark::getUpdatedAt).orElse(null);
        Instant updatedAt = progressUpdatedAt == null
                ? bookmarkUpdatedAt
                : bookmarkUpdatedAt == null || progressUpdatedAt.isAfter(bookmarkUpdatedAt)
                        ? progressUpdatedAt
                        : bookmarkUpdatedAt;
        return new DocumentState(
                IdPrefix.DOCUMENT.format(documentId),
                progress.map(ReadingProgress::getProgressPercent).orElse(BigDecimal.ZERO),
                bookmark.isPresent(),
                updatedAt
        );
    }

    private ReadingItem recentItem(RecentView recent, UUID userId) {
        UUID documentId = recent.getId().getDocumentId();
        ExtractedDocument document = requiredStoredDocument(documentId);
        KnowledgePackage pkg = requiredStoredPackage(recent.getPackageId());
        Optional<ReadingProgress> progress = progressRepository.findById(new ReadingProgressId(userId, documentId));
        boolean bookmarked = bookmarkRepository.existsByUserIdAndDocumentIdAndAnchor(
                userId, documentId, DEFAULT_BOOKMARK_ANCHOR
        );
        return item(document, pkg, progress, bookmarked, recent.getViewedAt());
    }

    private ReadingItem bookmarkItem(ReadingBookmark bookmark, UUID userId) {
        ExtractedDocument document = requiredStoredDocument(bookmark.getDocumentId());
        KnowledgePackage pkg = requiredStoredPackage(bookmark.getPackageId());
        Optional<ReadingProgress> progress = progressRepository.findById(
                new ReadingProgressId(userId, bookmark.getDocumentId())
        );
        Instant lastReadAt = recentViewRepository.findById(
                new RecentViewId(userId, bookmark.getDocumentId())
        ).map(RecentView::getViewedAt).orElse(null);
        return item(document, pkg, progress, true, lastReadAt);
    }

    private ReadingItem item(
            ExtractedDocument document,
            KnowledgePackage pkg,
            Optional<ReadingProgress> progress,
            boolean bookmarked,
            Instant lastReadAt
    ) {
        return new ReadingItem(
                IdPrefix.DOCUMENT.format(document.getId()),
                document.getTitle() == null || document.getTitle().isBlank()
                        ? document.getSourcePath() : document.getTitle(),
                IdPrefix.PACKAGE.format(pkg.getId()),
                pkg.getTitle(),
                IdPrefix.VERSION.format(document.getVersionId()),
                progress.map(ReadingProgress::getProgressPercent).orElse(BigDecimal.ZERO),
                bookmarked,
                lastReadAt,
                progress.map(ReadingProgress::getUpdatedAt).orElse(null)
        );
    }

    private ExtractedDocument requiredStoredDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Document not found"));
    }

    private KnowledgePackage requiredStoredPackage(UUID packageId) {
        return packageRepository.findById(packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
    }

    private record DocumentContext(ExtractedDocument document, KnowledgePackage pkg) {
    }
}
