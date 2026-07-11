package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock private CollectionRepository collectionRepository;
    @Mock private PackageAccessService accessService;
    @Mock private OperationLogService operationLogService;

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
}
