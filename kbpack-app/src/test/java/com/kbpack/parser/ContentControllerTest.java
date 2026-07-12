package com.kbpack.parser;

import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAsset;
import com.kbpack.pkg.PackageAssetRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.task.ParseTaskRepository;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import io.minio.GetObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    @Mock private PackageVersionRepository versionRepository;
    @Mock private KnowledgePackageRepository packageRepository;
    @Mock private PackageAssetRepository assetRepository;
    @Mock private ExtractedDocumentRepository documentRepository;
    @Mock private ParseTaskRepository taskRepository;
    @Mock private ObjectStorageService storage;
    @Mock private AuthService authService;

    private ContentController controller;
    private AppUser user;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        controller = new ContentController(versionRepository, packageRepository, assetRepository,
                documentRepository, taskRepository, storage, authService);
        user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setRole(AppUser.Role.viewer);
        authentication = new UsernamePasswordAuthenticationToken(user.getId().toString(), null);
        when(authService.requireUserById(user.getId().toString())).thenReturn(user);
    }

    @Test
    void forcesUploadedHtmlToDownloadWithSandboxedHeaders() throws Exception {
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        PackageVersion version = readableVersion(packageId, versionId);
        PackageAsset asset = new PackageAsset();
        asset.setVersionId(versionId);
        asset.setPath("docs/page.html");
        asset.setMimeType(MediaType.TEXT_HTML_VALUE);
        asset.setSize(42);
        when(assetRepository.findByVersionIdAndPath(versionId, "docs/page.html"))
                .thenReturn(Optional.of(asset));
        when(storage.packagesBucket()).thenReturn("packages");
        GetObjectResponse input = mock(GetObjectResponse.class);
        when(storage.open("packages", IdPrefix.PACKAGE.format(packageId) + "/"
                + IdPrefix.VERSION.format(versionId) + "/files/docs/page.html"))
                .thenReturn(input);

        var response = controller.asset(
                IdPrefix.VERSION.format(versionId), "/docs/page.html", authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy"))
                .isEqualTo("default-src 'none'; sandbox");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(response.getBody()).isNotNull();
        response.getBody().writeTo(output);
        assertThat(output.size()).isZero();
    }

    @Test
    void redirectsRelativeChapterLinkBackToReaderRoute() {
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        readableVersion(packageId, versionId);
        UUID documentId = UUID.randomUUID();
        ExtractedDocument document = mock(ExtractedDocument.class);
        when(document.getId()).thenReturn(documentId);
        when(documentRepository.findByVersionIdAndSourcePath(versionId, "chapters/next.md"))
                .thenReturn(Optional.of(document));

        var response = controller.asset(
                IdPrefix.VERSION.format(versionId), "/chapters/next.md", authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
                .hasToString("/documents/" + IdPrefix.DOCUMENT.format(documentId));
    }

    @Test
    void documentDetailIncludesSourcePath() {
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        readableVersion(packageId, versionId);
        ExtractedDocument document = mock(ExtractedDocument.class);
        when(document.getId()).thenReturn(documentId);
        when(document.getPackageId()).thenReturn(packageId);
        when(document.getVersionId()).thenReturn(versionId);
        when(document.getDocType()).thenReturn(ExtractedDocument.DocType.markdown);
        when(document.getSourcePath()).thenReturn("chapters/intro.md");
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.findByVersionIdOrderByOrderNoAsc(versionId)).thenReturn(List.of(document));

        Map<String, Object> response = controller.document(IdPrefix.DOCUMENT.format(documentId), authentication);

        assertThat(response.get("source_path")).isEqualTo("chapters/intro.md");
    }

    private PackageVersion readableVersion(UUID packageId, UUID versionId) {
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setPackageId(packageId);
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setOwnerId(user.getId());
        pkg.setVisibility("private");
        when(versionRepository.findActiveById(versionId)).thenReturn(Optional.of(version));
        when(packageRepository.findActiveById(packageId)).thenReturn(Optional.of(pkg));
        return version;
    }
}
