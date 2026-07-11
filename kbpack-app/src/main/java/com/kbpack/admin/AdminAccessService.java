package com.kbpack.admin;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {

    private final AuthService authService;

    public AdminAccessService(AuthService authService) {
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

    public AppUser requireAdministrator() {
        AppUser user = currentUser();
        if (user.getRole() != AppUser.Role.owner && user.getRole() != AppUser.Role.admin) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
