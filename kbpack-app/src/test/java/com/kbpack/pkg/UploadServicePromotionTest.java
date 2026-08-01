package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.task.ParseTaskRepository;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServicePromotionTest {

    @Test
    void newVersionWaitsForSuccessfulParsingBeforeReplacingCurrentVersion() {
        UploadLimitService limits = new UploadLimitService(null, null, null) {
            @Override
            public Limits current() {
                return new Limits(10_000, 10_000, 10, 10_000, 512);
            }
        };
        OperationLogService logs = mock(OperationLogService.class);
        PackageService packages = mock(PackageService.class);
        PackageVersionRepository versions = mock(PackageVersionRepository.class);
        PackageAssetRepository assets = mock(PackageAssetRepository.class);
        ParseTaskRepository tasks = mock(ParseTaskRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        UUID packageId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setCurrentVersionId(currentVersionId);
        when(packages.replaceUploadMetadata(
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pkg);
        when(versions.findActiveByPackageId(packageId)).thenReturn(List.of());
        when(versions.findMaxVersionNo(packageId)).thenReturn(1);
        doAnswer(invocation -> {
            PackageVersion version = invocation.getArgument(0);
            version.setId(UUID.randomUUID());
            return version;
        }).when(versions).saveAndFlush(any(PackageVersion.class));
        when(storage.originalBucket()).thenReturn("originals");
        when(storage.packagesBucket()).thenReturn("packages");
        UploadService service = new UploadService(
                limits, logs, packages, versions, assets, tasks, storage);
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "index.html",
                "text/html",
                "<!doctype html><html><body>content</body></html>"
                        .getBytes(StandardCharsets.UTF_8)
        );
        UploadService.UploadMetadata metadata = new UploadService.UploadMetadata(
                "Updated",
                "Description",
                KnowledgePackage.SourceType.manual,
                "Source",
                null,
                packageId,
                List.of("tag"),
                List.of(),
                "127.0.0.1"
        );

        UploadService.UploadResult result = service.upload(upload, metadata, user);

        assertThat(pkg.getCurrentVersionId()).isEqualTo(currentVersionId);
        assertThat(result.version().isPromoteOnSuccess()).isTrue();
        assertThat(result.version().getParseStatus()).isEqualTo(PackageVersion.ParseStatus.pending);
        var promotionOrder = inOrder(versions);
        promotionOrder.verify(versions).clearPromotionCandidates(packageId);
        promotionOrder.verify(versions).saveAndFlush(result.version());
        verify(packages).replaceUploadMetadata(
                packageId,
                "Updated",
                "Description",
                KnowledgePackage.SourceType.manual,
                "Source",
                List.of("tag"),
                List.of(),
                user,
                "127.0.0.1"
        );
    }
}
