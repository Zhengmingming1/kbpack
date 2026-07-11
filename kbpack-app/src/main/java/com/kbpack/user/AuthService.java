package com.kbpack.user;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED = 5;
    private static final int LOCK_MINUTES = 5;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AppUser authenticate(String username, String password) {
        AppUser user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getStatus() == AppUser.Status.disabled) {
            throw new ApiException(ErrorCode.FORBIDDEN, "账号已禁用");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(ErrorCode.USER_LOCKED);
        }
        // lock window expired → clear
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(Instant.now())) {
            user.setLockedUntil(null);
            user.setFailedLoginCount(0);
            if (user.getStatus() == AppUser.Status.locked) {
                user.setStatus(AppUser.Status.active);
            }
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int fails = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(fails);
            if (fails >= MAX_FAILED) {
                user.setStatus(AppUser.Status.locked);
                user.setLockedUntil(Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
            }
            userRepository.save(user);
            if (fails >= MAX_FAILED) {
                throw new ApiException(ErrorCode.USER_LOCKED);
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        if (user.getStatus() == AppUser.Status.locked) {
            user.setStatus(AppUser.Status.active);
        }
        userRepository.save(user);
        return user;
    }

    @Transactional(readOnly = true)
    public AppUser requireUserById(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(uuid)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
