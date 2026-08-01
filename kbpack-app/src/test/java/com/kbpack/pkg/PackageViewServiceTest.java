package com.kbpack.pkg;

import com.kbpack.user.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void packageViewExposesResourceCapabilities() {
        PackageVersionRepository versions = mock(PackageVersionRepository.class);
        PackageTagRepository packageTags = mock(PackageTagRepository.class);
        TagRepository tags = mock(TagRepository.class);
        PackageCollectionRepository packageCollections = mock(PackageCollectionRepository.class);
        CollectionRepository collections = mock(CollectionRepository.class);
        FavoriteRepository favorites = mock(FavoriteRepository.class);
        PackageViewService service = new PackageViewService(
                versions,
                packageTags,
                tags,
                packageCollections,
                collections,
                favorites,
                mock(org.springframework.jdbc.core.JdbcTemplate.class)
        );
        UUID packageId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setTitle("Package");
        pkg.setSlug("package");
        pkg.setOwnerId(ownerId);
        pkg.onCreate();
        when(packageTags.findAllByIdPackageId(packageId)).thenReturn(List.of());
        when(packageCollections.findAllByIdPackageId(packageId)).thenReturn(List.of());

        AppUser editorOwner = new AppUser();
        editorOwner.setId(ownerId);
        editorOwner.setRole(AppUser.Role.editor);
        AppUser viewer = new AppUser();
        viewer.setId(UUID.randomUUID());
        viewer.setRole(AppUser.Role.viewer);

        assertThat(service.listItem(pkg, editorOwner))
                .containsEntry("can_edit", true)
                .containsEntry("can_delete", true)
                .containsEntry("can_reparse", true)
                .containsEntry("can_manage_versions", true);
        assertThat(service.listItem(pkg, viewer))
                .containsEntry("can_edit", false)
                .containsEntry("can_delete", false)
                .containsEntry("can_reparse", false)
                .containsEntry("can_manage_versions", false);
    }

    @Test
    void deletedPackageKeepsCurrentVersionSummaryButDisablesEditing() {
        PackageVersionRepository versions = mock(PackageVersionRepository.class);
        PackageTagRepository packageTags = mock(PackageTagRepository.class);
        PackageCollectionRepository packageCollections = mock(PackageCollectionRepository.class);
        PackageViewService service = new PackageViewService(
                versions,
                packageTags,
                mock(TagRepository.class),
                packageCollections,
                mock(CollectionRepository.class),
                mock(FavoriteRepository.class),
                mock(org.springframework.jdbc.core.JdbcTemplate.class)
        );
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setTitle("Deleted package");
        pkg.setSlug("deleted-package");
        pkg.setOwnerId(UUID.randomUUID());
        pkg.setCurrentVersionId(versionId);
        pkg.onCreate();
        pkg.setDeletedAt(Instant.now());
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setPackageId(packageId);
        version.setVersionNo(3);
        version.setParseStatus(PackageVersion.ParseStatus.success);
        version.setFileCount(7);
        version.setUnpackedSize(4096L);
        when(versions.findById(versionId)).thenReturn(Optional.of(version));
        when(packageTags.findAllByIdPackageId(packageId)).thenReturn(List.of());
        when(packageCollections.findAllByIdPackageId(packageId)).thenReturn(List.of());
        AppUser owner = new AppUser();
        owner.setId(pkg.getOwnerId());
        owner.setRole(AppUser.Role.owner);

        assertThat(service.listItem(pkg, owner))
                .containsEntry("can_edit", false)
                .containsEntry("can_reparse", false)
                .containsEntry("can_manage_versions", false)
                .containsEntry("file_count", 7)
                .containsEntry("unpacked_size", 4096L)
                .satisfies(view -> assertThat(view.get("current_version")).isNotNull());
    }
}
