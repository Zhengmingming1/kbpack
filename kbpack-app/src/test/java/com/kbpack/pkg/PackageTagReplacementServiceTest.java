package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.search.SearchIndexUpdateCoordinator;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageTagReplacementServiceTest {

    @Mock private KnowledgePackageRepository packageRepository;
    @Mock private PackageVersionRepository versionRepository;
    @Mock private TagRepository tagRepository;
    @Mock private PackageTagRepository packageTagRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private PackageCollectionRepository packageCollectionRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private PackageAccessService accessService;
    @Mock private OperationLogService operationLogService;
    @Mock private SearchIndexUpdateCoordinator searchIndexUpdates;

    @InjectMocks
    private PackageService packageService;

    @Test
    void replacesLinkedTagWithExistingTag() {
        UUID packageId = UUID.randomUUID();
        UUID removedTagId = UUID.randomUUID();
        UUID existingTagId = UUID.randomUUID();
        String existingName = UUID.randomUUID().toString();
        AppUser actor = actor();
        KnowledgePackage pkg = pkg(packageId);
        PackageTag removedLink = link(packageId, removedTagId);
        Tag existingTag = tag(existingTagId, existingName);

        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(tagRepository.findAllByNameIn(any())).thenReturn(List.of(existingTag));
        when(packageTagRepository.findAllByIdPackageId(packageId)).thenReturn(List.of(removedLink));

        packageService.replaceTags(packageId, List.of(existingName), actor, null);

        verify(packageTagRepository).deleteAll(List.of(removedLink));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<PackageTag>> added = ArgumentCaptor.forClass(Iterable.class);
        verify(packageTagRepository).saveAll(added.capture());
        assertThat(added.getValue()).extracting(link -> link.getId().getTagId())
                .containsExactly(existingTagId);
        verify(tagRepository, never()).save(any(Tag.class));
        assertThat(pkg.getUpdatedAt()).isNotNull();
        verify(packageRepository).save(pkg);
        verify(searchIndexUpdates).refreshPackageAfterCommit(packageId);
    }

    @Test
    void createsNewTagAndDeduplicatesNormalizedNames() {
        UUID packageId = UUID.randomUUID();
        UUID createdTagId = UUID.randomUUID();
        String newName = UUID.randomUUID().toString();
        AppUser actor = actor();
        KnowledgePackage pkg = pkg(packageId);

        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(tagRepository.findAllByNameIn(any())).thenReturn(List.of());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag created = invocation.getArgument(0);
            created.setId(createdTagId);
            return created;
        });
        when(packageTagRepository.findAllByIdPackageId(packageId)).thenReturn(List.of());

        packageService.replaceTags(
                packageId, List.of(" " + newName + " ", newName), actor, null
        );

        ArgumentCaptor<Tag> created = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(created.capture());
        assertThat(created.getValue().getName()).isEqualTo(newName);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<PackageTag>> added = ArgumentCaptor.forClass(Iterable.class);
        verify(packageTagRepository).saveAll(added.capture());
        assertThat(added.getValue()).extracting(link -> link.getId().getTagId())
                .containsExactly(createdTagId);
        verify(tagRepository).findAllByNameIn(any());
    }

    @Test
    void clearsAllTags() {
        UUID packageId = UUID.randomUUID();
        AppUser actor = actor();
        KnowledgePackage pkg = pkg(packageId);
        PackageTag first = link(packageId, UUID.randomUUID());
        PackageTag second = link(packageId, UUID.randomUUID());

        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(packageTagRepository.findAllByIdPackageId(packageId)).thenReturn(List.of(first, second));

        packageService.replaceTags(packageId, List.of(), actor, null);

        verify(packageTagRepository).deleteAll(List.of(first, second));
        verify(packageTagRepository, never()).saveAll(any());
        verify(tagRepository, never()).findByName(any());
        verify(packageRepository).save(pkg);
    }

    @Test
    void rejectsBlankTagNameBeforeChangingLinks() {
        UUID packageId = UUID.randomUUID();
        AppUser actor = actor();
        KnowledgePackage pkg = pkg(packageId);
        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));

        assertThatThrownBy(() -> packageService.replaceTags(
                packageId, List.of("   "), actor, null
        )).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(tagRepository, never()).findByName(any());
        verify(tagRepository, never()).save(any());
        verify(packageTagRepository, never()).findAllByIdPackageId(packageId);
        verify(packageRepository, never()).save(pkg);
    }

    @Test
    void rejectsOverlongTagNameBeforeChangingLinks() {
        UUID packageId = UUID.randomUUID();
        AppUser actor = actor();
        KnowledgePackage pkg = pkg(packageId);
        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));

        assertThatThrownBy(() -> packageService.replaceTags(
                packageId, List.of("x".repeat(65)), actor, null
        )).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(tagRepository, never()).findByName(any());
        verify(tagRepository, never()).save(any());
        verify(packageTagRepository, never()).findAllByIdPackageId(packageId);
        verify(packageRepository, never()).save(pkg);
    }

    @Test
    void rejectsMoreThanFiftyTagsBeforeQueryingTagsOrLinks() {
        UUID packageId = UUID.randomUUID();
        AppUser actor = actor();
        KnowledgePackage pkg = pkg(packageId);
        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));

        assertThatThrownBy(() -> packageService.replaceTags(
                packageId,
                java.util.stream.IntStream.range(0, PackageService.MAX_TAGS_PER_REQUEST + 1)
                        .mapToObj(index -> "tag-" + index)
                        .toList(),
                actor,
                null
        )).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(tagRepository, never()).findAllByNameIn(any());
        verify(packageTagRepository, never()).findAllByIdPackageId(packageId);
        verify(packageRepository, never()).save(pkg);
    }

    @Test
    void addTagsUsesTheSamePackageLockAndTouchesThePackage() {
        UUID packageId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        AppUser actor = actor();
        KnowledgePackage pkg = pkg(packageId);
        Tag tag = tag(tagId, "existing");
        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(tagRepository.findAllByNameIn(any())).thenReturn(List.of(tag));
        when(packageTagRepository.findAllByIdPackageId(packageId)).thenReturn(List.of());

        packageService.addTags(packageId, List.of("existing"), actor, null);

        verify(packageRepository).findActiveByIdForUpdate(packageId);
        verify(packageRepository).save(pkg);
        verify(packageTagRepository).saveAll(any());
        assertThat(pkg.getUpdatedAt()).isNotNull();
    }

    @Test
    void removeTagUsesTheSamePackageLockAndTouchesThePackage() {
        UUID packageId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        AppUser actor = actor();
        KnowledgePackage pkg = pkg(packageId);
        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(tagRepository.existsById(tagId)).thenReturn(true);

        packageService.removeTag(packageId, tagId, actor, null);

        verify(packageRepository).findActiveByIdForUpdate(packageId);
        verify(packageTagRepository).deleteByIdPackageIdAndIdTagId(packageId, tagId);
        verify(packageRepository).save(pkg);
        assertThat(pkg.getUpdatedAt()).isNotNull();
    }

    private AppUser actor() {
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        actor.setRole(AppUser.Role.editor);
        return actor;
    }

    private KnowledgePackage pkg(UUID packageId) {
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        return pkg;
    }

    private Tag tag(UUID tagId, String name) {
        Tag tag = new Tag();
        tag.setId(tagId);
        tag.setName(name);
        return tag;
    }

    private PackageTag link(UUID packageId, UUID tagId) {
        return new PackageTag(new PackageTagId(packageId, tagId));
    }
}
