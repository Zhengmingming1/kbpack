package com.kbpack.pkg;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PackageViewServiceTest {

    @Test
    void versionViewIncludesEntryFile() {
        PackageViewService service = new PackageViewService(
                mock(PackageVersionRepository.class),
                mock(PackageTagRepository.class),
                mock(TagRepository.class),
                mock(PackageCollectionRepository.class),
                mock(CollectionRepository.class),
                mock(FavoriteRepository.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class)
        );
        PackageVersion version = new PackageVersion();
        version.setId(UUID.randomUUID());
        version.setEntryFile("index.html");
        version.setOriginalFilename("archive.zip");
        version.setParseStatus(PackageVersion.ParseStatus.success);
        version.onCreate();

        assertThat(service.versionView(version, version.getId()))
                .containsEntry("entry_file", "index.html");
    }
}
