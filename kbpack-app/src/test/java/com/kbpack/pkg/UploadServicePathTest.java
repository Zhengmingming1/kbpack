package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static void assertUnsafe(String path) {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> UploadService.normalizeEntryPath(path, 512)
        );
        assertEquals(ErrorCode.ARCHIVE_UNSAFE, exception.getErrorCode());
    }
}
