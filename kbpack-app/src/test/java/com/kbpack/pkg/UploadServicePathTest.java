package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadServicePathTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "../secret.txt",
            "assets/../../secret.txt",
            "assets/../secret.txt"
    })
    void rejectsParentDirectorySegments(String path) {
        assertUnsafe(path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/etc/passwd",
            "C:\\Windows\\win.ini",
            "\\\\server\\share\\file.html"
    })
    void rejectsAbsolutePaths(String path) {
        assertUnsafe(path);
    }

    @Test
    void normalizesBackslashesInSafeRelativePath() {
        assertEquals(
                "assets/chapters/intro.md",
                UploadService.normalizeEntryPath("assets\\chapters\\intro.md", 512)
        );
    }

    @Test
    void rejectsZipSlipWrittenWithBackslashes() {
        assertUnsafe("assets\\..\\..\\secret.txt");
    }

    @Test
    void countsIgnoredMetadataAgainstUnpackedSizeLimit() throws IOException {
        UploadService service = serviceWithLimits(new UploadLimitService.Limits(10_000, 1, 10, 10, 512));
        MockMultipartFile upload = new MockMultipartFile(
                "file", "metadata.zip", "application/zip", zipWithMetadata(8));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(upload, metadata(), user()));

        assertEquals(ErrorCode.UPLOAD_LIMIT_EXCEEDED, exception.getErrorCode(), exception.getMessage());
        assertTrue(exception.getMessage().contains("解压后总大小"));
    }

    @Test
    void countsIgnoredMetadataAgainstFileCountLimit() throws IOException {
        UploadService service = serviceWithLimits(new UploadLimitService.Limits(10_000, 100, 0, 100, 512));
        MockMultipartFile upload = new MockMultipartFile(
                "file", "metadata.zip", "application/zip", zipWithMetadata(1));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(upload, metadata(), user()));

        assertEquals(ErrorCode.UPLOAD_LIMIT_EXCEEDED, exception.getErrorCode(), exception.getMessage());
        assertTrue(exception.getMessage().contains("文件数"));
    }

    private static void assertUnsafe(String path) {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> UploadService.normalizeEntryPath(path, 512)
        );
        assertEquals(ErrorCode.ARCHIVE_UNSAFE, exception.getErrorCode());
    }

    private static UploadService serviceWithLimits(UploadLimitService.Limits limits) {
        UploadLimitService limitService = new UploadLimitService(null, null, null) {
            @Override
            public Limits current() {
                return limits;
            }
        };
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(UUID.randomUUID());
        PackageService packageService = mock(PackageService.class);
        when(packageService.createDraft(anyString(), anyString(), any(), anyString(), any())).thenReturn(pkg);
        PackageVersionRepository versionRepository = mock(PackageVersionRepository.class);
        when(versionRepository.findActiveByPackageId(pkg.getId())).thenReturn(List.of());
        return new UploadService(limitService, null, packageService, null,
                versionRepository, null, null, null);
    }

    private static UploadService.UploadMetadata metadata() {
        return new UploadService.UploadMetadata(
                "Test", "Description", KnowledgePackage.SourceType.manual, "Test source",
                null, null, List.of(), List.of(), "127.0.0.1");
    }

    private static AppUser user() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static byte[] zipWithMetadata(int... entrySizes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int index = 0; index < entrySizes.length; index++) {
                zip.putNextEntry(new ZipEntry("__MACOSX/metadata-" + index + ".bin"));
                zip.write(new byte[entrySizes[index]]);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
