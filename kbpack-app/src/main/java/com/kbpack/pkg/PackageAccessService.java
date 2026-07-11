package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PackageAccessService {

    private final KnowledgePackageRepository packageRepository;
    private final AuthService authService;

    public PackageAccessService(KnowledgePackageRepository packageRepository, AuthService authService) {
        this.packageRepository = packageRepository;
        this.authService = authService;
    }

    public AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return authService.requireUserById(authentication.getPrincipal().toString());
    }

    public KnowledgePackage requireReadable(UUID packageId, AppUser user) {
        KnowledgePackage pkg = packageRepository.findActiveById(packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        if (!canRead(pkg, user)) {
            throw new ApiException(ErrorCode.PACKAGE_NOT_FOUND);
        }
        return pkg;
    }

    public KnowledgePackage requireWritable(UUID packageId, AppUser user) {
        KnowledgePackage pkg = requireReadable(packageId, user);
        if (isAdministrator(user)) {
            return pkg;
        }
        if (user.getRole() == AppUser.Role.editor && user.getId().equals(pkg.getOwnerId())) {
            return pkg;
        }
        throw new ApiException(ErrorCode.FORBIDDEN);
    }

    public void requireContentWriter(AppUser user) {
        if (user.getRole() == AppUser.Role.viewer) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }

    public void requireAdministrator(AppUser user) {
        if (!isAdministrator(user)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean canRead(KnowledgePackage pkg, AppUser user) {
        return isAdministrator(user)
                || user.getId().equals(pkg.getOwnerId())
                || "team".equals(pkg.getVisibility())
                || "public".equals(pkg.getVisibility());
    }

    public boolean isAdministrator(AppUser user) {
        return user.getRole() == AppUser.Role.owner || user.getRole() == AppUser.Role.admin;
    }
}
