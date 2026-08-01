package com.kbpack.reading;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.parser.ExtractedDocument;
import com.kbpack.parser.ExtractedDocumentRepository;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAccessService;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Mock private ReadingProgressRepository progressRepository;
    @Mock private RecentViewRepository recentViewRepository;
    @Mock private ReadingBookmarkRepository bookmarkRepository;
    @Mock private ExtractedDocumentRepository documentRepository;
    @Mock private KnowledgePackageRepository packageRepository;
    @Mock private PackageVersionRepository versionRepository;
    @Mock private PackageAccessService accessService;

    private ReadingService service;

    @BeforeEach
    void setUp() {
        service = new ReadingService(
                progressRepository,
                recentViewRepository,
                bookmarkRepository,
                documentRepository,
                packageRepository,
                versionRepository,
                accessService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void springCanConstructTheRuntimeService() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("readingProgressRepository", progressRepository);
        factory.registerSingleton("recentViewRepository", recentViewRepository);
        factory.registerSingleton("readingBookmarkRepository", bookmarkRepository);
        factory.registerSingleton("extractedDocumentRepository", documentRepository);
        factory.registerSingleton("knowledgePackageRepository", packageRepository);
        factory.registerSingleton("packageVersionRepository", versionRepository);
        factory.registerSingleton("packageAccessService", accessService);

        assertThat(factory.createBean(ReadingService.class)).isNotNull();
    }

    @Test
    void updatesDocumentScopedProgressAndRecentViewTogether() {
        AppUser user = user(AppUser.Role.viewer);
        DocumentFixture fixture = readableDocument(user);
        ReadingProgressId progressId = new ReadingProgressId(user.getId(), fixture.documentId());
        ReadingProgress storedProgress = new ReadingProgress(progressId);
        storedProgress.update(
                fixture.versionId(), fixture.packageId(), null, new BigDecimal("42.50"), 0, NOW
        );
        when(progressRepository.findById(progressId)).thenReturn(Optional.of(storedProgress));
        when(bookmarkRepository.findByUserIdAndDocumentIdAndAnchor(
                user.getId(), fixture.documentId(), "")).thenReturn(Optional.empty());

        ReadingService.DocumentState result = service.updateProgress(
                fixture.documentId(), new BigDecimal("42.50"), user
        );

        assertThat(result.progress()).isEqualByComparingTo("42.50");
        assertThat(result.is_bookmarked()).isFalse();
        assertThat(result.updated_at()).isEqualTo(NOW);
        verify(progressRepository).upsert(
                user.getId(), fixture.documentId(), fixture.packageId(), fixture.versionId(),
                null, new BigDecimal("42.50"), 0, NOW
        );
        verify(recentViewRepository).upsert(
                user.getId(), fixture.documentId(), fixture.packageId(), fixture.versionId(), NOW
        );
    }

    @Test
    void rejectsDocumentFromSoftDeletedVersion() {
        AppUser user = user(AppUser.Role.viewer);
        UUID documentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ExtractedDocument document = document(documentId, packageId, versionId);
        KnowledgePackage pkg = knowledgePackage(packageId, user.getId());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(accessService.requireReadable(packageId, user)).thenReturn(pkg);
        when(versionRepository.findActiveByIdAndPackageId(versionId, packageId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProgress(documentId, BigDecimal.TEN, user))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verifyNoInteractions(progressRepository, recentViewRepository);
    }

    @Test
    void creatingAnExistingDocumentBookmarkIsIdempotent() {
        AppUser user = user(AppUser.Role.viewer);
        DocumentFixture fixture = readableDocument(user);
        ReadingBookmark existing = new ReadingBookmark(
                user.getId(), fixture.packageId(), fixture.versionId(), fixture.documentId(),
                "", null, null, NOW
        );
        when(bookmarkRepository.existsByUserIdAndDocumentIdAndAnchor(
                user.getId(), fixture.documentId(), "")).thenReturn(true);
        when(bookmarkRepository.findByUserIdAndDocumentIdAndAnchor(
                user.getId(), fixture.documentId(), "")).thenReturn(Optional.of(existing));
        when(progressRepository.findById(new ReadingProgressId(user.getId(), fixture.documentId())))
                .thenReturn(Optional.empty());

        ReadingService.DocumentState state = service.setBookmarked(fixture.documentId(), true, user);

        assertThat(state.is_bookmarked()).isTrue();
        assertThat(state.progress()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    void openingDocumentStateRefreshesRecentViewWithoutCreatingProgress() {
        AppUser user = user(AppUser.Role.viewer);
        DocumentFixture fixture = readableDocument(user);
        when(progressRepository.findById(new ReadingProgressId(user.getId(), fixture.documentId())))
                .thenReturn(Optional.empty());
        when(bookmarkRepository.findByUserIdAndDocumentIdAndAnchor(
                user.getId(), fixture.documentId(), "")).thenReturn(Optional.empty());

        ReadingService.DocumentState state = service.state(fixture.documentId(), user);

        assertThat(state.progress()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(recentViewRepository).upsert(
                user.getId(), fixture.documentId(), fixture.packageId(), fixture.versionId(), NOW
        );
        verify(progressRepository, never()).save(any());
    }

    @Test
    void documentStateUsesTheMostRecentProgressOrBookmarkUpdate() {
        AppUser user = user(AppUser.Role.viewer);
        DocumentFixture fixture = readableDocument(user);
        ReadingProgressId progressId = new ReadingProgressId(user.getId(), fixture.documentId());
        ReadingProgress progress = new ReadingProgress(progressId);
        progress.update(
                fixture.versionId(), fixture.packageId(), null, BigDecimal.TEN, 0,
                NOW.minusSeconds(60)
        );
        ReadingBookmark bookmark = new ReadingBookmark(
                user.getId(), fixture.packageId(), fixture.versionId(), fixture.documentId(),
                "", null, null, NOW
        );
        when(progressRepository.findById(progressId)).thenReturn(Optional.of(progress));
        when(bookmarkRepository.findByUserIdAndDocumentIdAndAnchor(
                user.getId(), fixture.documentId(), "")).thenReturn(Optional.of(bookmark));

        ReadingService.DocumentState state = service.state(fixture.documentId(), user);

        assertThat(state.updated_at()).isEqualTo(NOW);
    }

    @Test
    void rejectsProgressOutsidePercentageRangeBeforeLoadingDocument() {
        AppUser user = user(AppUser.Role.viewer);

        assertThatThrownBy(() -> service.updateProgress(UUID.randomUUID(), new BigDecimal("100.01"), user))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        verify(documentRepository, never()).findById(any());
    }

    private DocumentFixture readableDocument(AppUser user) {
        UUID documentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ExtractedDocument document = document(documentId, packageId, versionId);
        KnowledgePackage pkg = knowledgePackage(packageId, user.getId());
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setPackageId(packageId);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(accessService.requireReadable(packageId, user)).thenReturn(pkg);
        when(versionRepository.findActiveByIdAndPackageId(versionId, packageId))
                .thenReturn(Optional.of(version));
        return new DocumentFixture(documentId, packageId, versionId);
    }

    private ExtractedDocument document(UUID documentId, UUID packageId, UUID versionId) {
        ExtractedDocument document = org.mockito.Mockito.mock(ExtractedDocument.class);
        when(document.getPackageId()).thenReturn(packageId);
        when(document.getVersionId()).thenReturn(versionId);
        return document;
    }

    private KnowledgePackage knowledgePackage(UUID packageId, UUID ownerId) {
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setOwnerId(ownerId);
        pkg.setVisibility("private");
        return pkg;
    }

    private AppUser user(AppUser.Role role) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }

    private record DocumentFixture(UUID documentId, UUID packageId, UUID versionId) {
    }
}
