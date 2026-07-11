package com.kbpack.search;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.parser.ExtractedDocumentRepository;
import com.kbpack.parser.SearchChunkRepository;
import com.kbpack.pkg.CollectionRepository;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageCollectionRepository;
import com.kbpack.pkg.PackageTagRepository;
import com.kbpack.pkg.PackageVersionRepository;
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
                1, 20, authentication);

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
                1, 20, authentication))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("package_id");
        verifyNoInteractions(indexService);
    }
}
