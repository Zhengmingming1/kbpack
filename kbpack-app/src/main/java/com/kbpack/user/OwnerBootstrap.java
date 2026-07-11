package com.kbpack.user;

import com.kbpack.common.config.KbpackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the initial owner account when app_user is empty (ADR-9 / FR-AUTH-01).
 */
@Component
public class OwnerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KbpackProperties properties;

    public OwnerBootstrap(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            KbpackProperties properties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        String username = properties.getInit().getAdminUsername();
        String password = properties.getInit().getAdminPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("INIT_ADMIN_USERNAME/PASSWORD not set; skip owner bootstrap");
            return;
        }

        AppUser owner = new AppUser();
        owner.setUsername(username.trim());
        owner.setPasswordHash(passwordEncoder.encode(password));
        owner.setDisplayName(username.trim());
        owner.setRole(AppUser.Role.owner);
        owner.setStatus(AppUser.Status.active);
        userRepository.save(owner);
        log.info("Created initial owner account: {}", username);
    }
}
