package com.kbpack.recycle;

import com.kbpack.admin.OperationLogService;
import com.kbpack.admin.RuntimeSettingService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAccessService;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.search.SearchIndexUpdateCoordinator;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecycleBinServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Mock private KnowledgePackageRepository packageRepository;
    @Mock private PackageVersionRepository versionRepository;
    @Mock private PackageAccessService accessService;
    @Mock private RuntimeSettingService runtimeSettings;
    @Mock private SearchIndexUpdateCoordinator indexCoordinator;
    @Mock private OperationLogService operationLogService;
    @Mock private PackagePurgeService purgeService;

    private RecycleBinService service;

    @BeforeEach
    void setUp() {
        service = new RecycleBinService(
                packageRepository,
                versionRepository,
                accessService,
                runtimeSettings,
                indexCoordinator,
                operationLogService,
                purgeService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void springCanConstructTheRuntimeService() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("knowledgePackageRepository", packageRepository);
        factory.registerSingleton("packageVersionRepository", versionRepository);
        factory.registerSingleton("packageAccessService", accessService);
        factory.registerSingleton("runtimeSettingService", runtimeSettings);
        factory.registerSingleton("searchIndexUpdateCoordinator", indexCoordinator);
        factory.registerSingleton("operationLogService", operationLogService);
        factory.registerSingleton("packagePurgeService", purgeService);

        assertThat(factory.createBean(RecycleBinService.class)).isNotNull();
    }

    @Test
    void editorListIsOwnerScopedAndIncludesRetentionDeadline() {
        AppUser editor = user(AppUser.Role.editor);
        KnowledgePackage pkg = deletedPackage(editor.getId(), NOW.minus(2, ChronoUnit.DAYS));
        PageRequest pageable = PageRequest.of(0, 20);
        when(runtimeSettings.cleanupRetentionDays()).thenReturn(30);
        when(accessService.isAdministrator(editor)).thenReturn(false);
        when(packageRepository.findDeletedVisibleTo(editor.getId(), false, pageable))
                .thenReturn(new PageImpl<>(List.of(pkg), pageable, 1));
        when(versionRepository.countByPackageId(pkg.getId())).thenReturn(3L);

        RecycleBinService.DeletedPackage item = service.list(editor, pageable).getContent().getFirst();

        assertThat(item.canRestore()).isTrue();
        assertThat(item.purgeAt()).isEqualTo(pkg.getDeletedAt().plus(30, ChronoUnit.DAYS));
        assertThat(item.versionsCount()).isEqualTo(3);
        verify(accessService).requireContentWriter(editor);
        verify(packageRepository).findDeletedVisibleTo(editor.getId(), false, pageable);
    }

    @Test
    void restoreOnlyRevivesVersionsDeletedWithThePackage() {
        AppUser editor = user(AppUser.Role.editor);
        Instant deletedAt = NOW.minus(3, ChronoUnit.DAYS);
        KnowledgePackage pkg = deletedPackage(editor.getId(), deletedAt);
        PackageVersion packageDeletedVersion = new PackageVersion();
        packageDeletedVersion.setId(UUID.randomUUID());
        packageDeletedVersion.setPackageId(pkg.getId());
        packageDeletedVersion.setDeletedAt(deletedAt);
        when(packageRepository.findDeletedByIdForUpdate(pkg.getId())).thenReturn(Optional.of(pkg));
        when(accessService.isAdministrator(editor)).thenReturn(false);
        when(runtimeSettings.cleanupRetentionDays()).thenReturn(30);
        when(versionRepository.findByPackageIdAndDeletedAt(pkg.getId(), deletedAt))
                .thenReturn(List.of(packageDeletedVersion));

        KnowledgePackage restored = service.restore(pkg.getId(), editor, "127.0.0.1");

        assertThat(restored.getDeletedAt()).isNull();
        assertThat(packageDeletedVersion.getDeletedAt()).isNull();
        verify(versionRepository).findByPackageIdAndDeletedAt(pkg.getId(), deletedAt);
        verify(packageRepository).save(pkg);
        verify(indexCoordinator).refreshPackageAfterCommit(pkg.getId());
        verify(operationLogService).record(
                eq(editor.getId()), eq("package.restore"), eq("knowledge_package"), eq(pkg.getId()),
                any(), eq("127.0.0.1")
        );
    }

    @Test
    void expiredPackageCannotBeRestored() {
        AppUser owner = user(AppUser.Role.owner);
        KnowledgePackage pkg = deletedPackage(UUID.randomUUID(), NOW.minus(30, ChronoUnit.DAYS));
        when(packageRepository.findDeletedByIdForUpdate(pkg.getId())).thenReturn(Optional.of(pkg));
        when(accessService.isAdministrator(owner)).thenReturn(true);
        when(runtimeSettings.cleanupRetentionDays()).thenReturn(30);

        assertThatThrownBy(() -> service.restore(pkg.getId(), owner, "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(versionRepository, never()).findByPackageIdAndDeletedAt(any(), any());
        verify(packageRepository, never()).save(any());
    }

    @Test
    void editorCannotPurgeAnotherUsersPackage() {
        AppUser editor = user(AppUser.Role.editor);
        KnowledgePackage pkg = deletedPackage(UUID.randomUUID(), NOW.minus(1, ChronoUnit.DAYS));
        when(packageRepository.findById(pkg.getId())).thenReturn(Optional.of(pkg));
        when(accessService.isAdministrator(editor)).thenReturn(false);

        assertThatThrownBy(() -> service.purge(pkg.getId(), editor, "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PACKAGE_NOT_FOUND));
        verify(purgeService, never()).purge(any());
    }

    @Test
    void administratorCanPurgeBeforeRetentionDeadline() {
        AppUser admin = user(AppUser.Role.admin);
        KnowledgePackage pkg = deletedPackage(UUID.randomUUID(), NOW.minus(1, ChronoUnit.DAYS));
        when(packageRepository.findById(pkg.getId())).thenReturn(Optional.of(pkg));
        when(accessService.isAdministrator(admin)).thenReturn(true);

        service.purge(pkg.getId(), admin, "127.0.0.1");

        verify(purgeService).purge(pkg.getId());
        verify(operationLogService).record(
                eq(admin.getId()), eq("package.purge"), eq("knowledge_package"), eq(pkg.getId()),
                any(), eq("127.0.0.1")
        );
    }

    private KnowledgePackage deletedPackage(UUID ownerId, Instant deletedAt) {
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(UUID.randomUUID());
        pkg.setOwnerId(ownerId);
        pkg.setDeletedAt(deletedAt);
        return pkg;
    }

    private AppUser user(AppUser.Role role) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        return user;
    }
}
