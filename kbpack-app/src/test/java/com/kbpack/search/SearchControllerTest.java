package com.kbpack.search;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.parser.ExtractedDocumentRepository;
import com.kbpack.parser.SearchChunk;
import com.kbpack.parser.SearchChunkRepository;
import com.kbpack.pkg.CollectionRepository;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageCollectionRepository;
import com.kbpack.pkg.PackageTagRepository;
import com.kbpack.pkg.PackageTag;
import com.kbpack.pkg.PackageTagId;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.pkg.Tag;
import com.kbpack.pkg.TagRepository;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock private SearchIndexService indexService;
    @Mock private SearchChunkRepository chunkRepository;
    @Mock private ExtractedDocumentRepository documentRepository;
    @Mock private KnowledgePackageRepository packageRepository;
    @Mock private AuthService authService;
    @Mock private PackageVersionRepository versionRepository;
    @Mock private PackageTagRepository packageTagRepository;
    @Mock private TagRepository tagRepository;
    @Mock private PackageCollectionRepository packageCollectionRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private OperationLogService operationLogService;

    @InjectMocks private SearchController controller;

    @Test
    void addsPackageIdToMeilisearchFilter() {
        UUID userId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setRole(AppUser.Role.owner);
        var authentication = new UsernamePasswordAuthenticationToken(userId.toString(), "");
        when(authService.requireUserById(userId.toString())).thenReturn(user);
        ArgumentCaptor<String> filter = ArgumentCaptor.forClass(String.class);
        when(indexService.search(eq("needle"), eq(1), eq(20), filter.capture()))
                .thenReturn(new SearchIndexService.SearchPage(0, List.of()));

        controller.search(
                "needle", null, null, null, null, IdPrefix.PACKAGE.format(packageId),
                "current", 1, 20, authentication);

        assertThat(filter.getValue())
                .isEqualTo("package_id = \"" + IdPrefix.PACKAGE.format(packageId) + "\"");
    }

    @Test
    void rejectsMalformedPackageId() {
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setRole(AppUser.Role.owner);
        var authentication = new UsernamePasswordAuthenticationToken(userId.toString(), "");
        when(authService.requireUserById(userId.toString())).thenReturn(user);

        assertThatThrownBy(() -> controller.search(
                "needle", null, null, null, null, "not-a-package-id",
                "current", 1, 20, authentication))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("package_id");
        verifyNoInteractions(indexService);
    }

    @Test
    void fallbackSearchExcludesHistoricalVersions() {
        UUID userId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();
        UUID historicalVersionId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setRole(AppUser.Role.owner);
        var authentication = new UsernamePasswordAuthenticationToken(userId.toString(), "");
        when(authService.requireUserById(userId.toString())).thenReturn(user);
        when(indexService.search(eq("needle"), eq(1), eq(20), eq("")))
                .thenThrow(new ApiException(com.kbpack.common.error.ErrorCode.SEARCH_UNAVAILABLE));
        SearchChunk historical = new SearchChunk();
        historical.setPackageId(packageId);
        historical.setVersionId(historicalVersionId);
        historical.setContent("needle");
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setOwnerId(userId);
        pkg.setVisibility("private");
        pkg.setCurrentVersionId(currentVersionId);
        when(chunkRepository.findAll()).thenReturn(List.of(historical));
        when(packageRepository.findActiveById(packageId)).thenReturn(java.util.Optional.of(pkg));

        var result = controller.search(
                "needle", null, null, null, null, null,
                "current", 1, 20, authentication);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verifyNoInteractions(versionRepository, documentRepository);
    }

    @Test
    void explicitHistoryScopeReturnsSuccessfulHistoricalVersion() {
        UUID userId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID historicalVersionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setRole(AppUser.Role.owner);
        var authentication = new UsernamePasswordAuthenticationToken(userId.toString(), "");
        when(authService.requireUserById(userId.toString())).thenReturn(user);

        SearchChunk historical = new SearchChunk();
        historical.setPackageId(packageId);
        historical.setVersionId(historicalVersionId);
        historical.setDocumentId(documentId);
        historical.setContent("needle in history");
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setTitle("Package");
        pkg.setOwnerId(userId);
        pkg.setVisibility("private");
        pkg.setCurrentVersionId(UUID.randomUUID());
        PackageVersion version = new PackageVersion();
        version.setPackageId(packageId);
        version.setVersionNo(2);
        version.setParseStatus(PackageVersion.ParseStatus.success);
        var document = mock(com.kbpack.parser.ExtractedDocument.class);
        when(document.getId()).thenReturn(documentId);
        when(document.getTitle()).thenReturn("Historical chapter");
        when(chunkRepository.findAll()).thenReturn(List.of(historical));
        when(packageRepository.findActiveById(packageId)).thenReturn(java.util.Optional.of(pkg));
        when(versionRepository.findActiveById(historicalVersionId)).thenReturn(java.util.Optional.of(version));
        when(documentRepository.findById(documentId)).thenReturn(java.util.Optional.of(document));
        Tag tag = new Tag();
        tag.setId(tagId);
        tag.setName("history-tag");
        when(packageTagRepository.findAllByIdPackageId(packageId))
                .thenReturn(List.of(new PackageTag(new PackageTagId(packageId, tagId))));
        when(tagRepository.findAllById(List.of(tagId))).thenReturn(List.of(tag));

        var result = controller.search(
                "needle", null, null, null, null, null,
                "history", 1, 20, authentication);

        assertThat(result.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item).containsEntry("version_no", 2);
                    assertThat(item).containsEntry("is_current", false);
                    assertThat(item).containsEntry("tags", List.of("history-tag"));
                });
        verifyNoInteractions(indexService);
    }
}
