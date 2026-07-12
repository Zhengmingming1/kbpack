package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.search.SearchIndexUpdateCoordinator;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private PackageAccessService accessService;
    @Mock private OperationLogService operationLogService;
    @Mock private PackageCollectionRepository packageCollectionRepository;
    @Mock private SearchIndexUpdateCoordinator searchIndexUpdates;

    @InjectMocks
    private CollectionService collectionService;

    @Test
    void rejectsParentChangeThatCreatesCycle() {
        UUID collectionId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        CollectionEntity collection = new CollectionEntity();
        collection.setId(collectionId);
        CollectionEntity child = new CollectionEntity();
        child.setId(childId);
        child.setParentId(collectionId);
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        actor.setRole(AppUser.Role.editor);

        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(collectionRepository.findById(childId)).thenReturn(Optional.of(child));

        CollectionService.Patch patch = new CollectionService.Patch(
                false, null, true, childId, false, null
        );

        assertThatThrownBy(() -> collectionService.patch(
                collectionId, patch, actor, "127.0.0.1"
        )).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void deletingParentRefreshesPackagesLinkedToDescendantCollections() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        CollectionEntity root = new CollectionEntity();
        root.setId(rootId);
        root.setName("Root");
        CollectionEntity child = new CollectionEntity();
        child.setId(childId);
        child.setName("Child");
        child.setParentId(rootId);
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        actor.setRole(AppUser.Role.owner);

        when(collectionRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(collectionRepository.findAll()).thenReturn(List.of(root, child));
        when(packageCollectionRepository.findAllByIdCollectionIdIn(any()))
                .thenReturn(List.of(new PackageCollection(new PackageCollectionId(packageId, childId))));

        collectionService.delete(rootId, actor, "127.0.0.1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> subtree = ArgumentCaptor.forClass(Collection.class);
        verify(packageCollectionRepository).findAllByIdCollectionIdIn(subtree.capture());
        assertThat(subtree.getValue()).containsExactlyInAnyOrder(rootId, childId);
        verify(searchIndexUpdates).refreshPackagesAfterCommit(List.of(packageId));
    }
}
