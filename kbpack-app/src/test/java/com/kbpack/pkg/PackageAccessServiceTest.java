package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageAccessServiceTest {

    @Mock
    private KnowledgePackageRepository packageRepository;

    @Mock
    private AuthService authService;

    @Test
    void hidesAnotherUsersPrivatePackage() {
        UUID packageId = UUID.randomUUID();
        AppUser viewer = user(AppUser.Role.viewer, UUID.randomUUID());
        KnowledgePackage pkg = knowledgePackage(UUID.randomUUID(), "private");
        when(packageRepository.findActiveById(packageId)).thenReturn(Optional.of(pkg));

        PackageAccessService service = new PackageAccessService(packageRepository, authService);

        assertThatThrownBy(() -> service.requireReadable(packageId, viewer))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PACKAGE_NOT_FOUND));
    }

    @Test
    void viewerCanReadPublicPackageButCannotModifyIt() {
        UUID packageId = UUID.randomUUID();
        AppUser viewer = user(AppUser.Role.viewer, UUID.randomUUID());
        KnowledgePackage pkg = knowledgePackage(UUID.randomUUID(), "public");
        when(packageRepository.findActiveById(packageId)).thenReturn(Optional.of(pkg));

        PackageAccessService service = new PackageAccessService(packageRepository, authService);

        assertThat(service.requireReadable(packageId, viewer)).isSameAs(pkg);
        assertThatThrownBy(() -> service.requireWritable(packageId, viewer))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void editorCanModifyOwnPrivatePackage() {
        UUID editorId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        AppUser editor = user(AppUser.Role.editor, editorId);
        KnowledgePackage pkg = knowledgePackage(editorId, "private");
        when(packageRepository.findActiveById(packageId)).thenReturn(Optional.of(pkg));

        PackageAccessService service = new PackageAccessService(packageRepository, authService);

        assertThat(service.requireWritable(packageId, editor)).isSameAs(pkg);
    }

    private AppUser user(AppUser.Role role, UUID id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private KnowledgePackage knowledgePackage(UUID ownerId, String visibility) {
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setOwnerId(ownerId);
        pkg.setVisibility(visibility);
        return pkg;
    }
}
