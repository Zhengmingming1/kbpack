package com.kbpack.common.archive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArchivePathsTest {
    @Test
    void detectsMacOsArchiveMetadataWithoutHidingNormalDotFiles() {
        assertThat(ArchivePaths.isPlatformMetadata("__MACOSX/kb/._index.html")).isTrue();
        assertThat(ArchivePaths.isPlatformMetadata("kb/__MACOSX/metadata")).isTrue();
        assertThat(ArchivePaths.isPlatformMetadata("kb/assets/._chapter.md")).isTrue();
        assertThat(ArchivePaths.isPlatformMetadata("kb/.DS_Store")).isTrue();

        assertThat(ArchivePaths.isPlatformMetadata("kb/.env.example")).isFalse();
        assertThat(ArchivePaths.isPlatformMetadata("kb/assets/__MACOSX-guide.md")).isFalse();
        assertThat(ArchivePaths.isPlatformMetadata("kb/index.html")).isFalse();
    }
}
