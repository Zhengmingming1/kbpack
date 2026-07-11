package com.kbpack.user;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthAuditService authAuditService;
    private final AppUserRepository userRepository;

    public AuthController(AuthService authService, AuthAuditService authAuditService, AppUserRepository userRepository) {
        this.authService = authService;
        this.authAuditService = authAuditService;
        this.userRepository = userRepository;
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record UserView(String id, String username, String display_name, String role) {
        static UserView from(AppUser user) {
            return new UserView(
                    IdPrefix.USER.format(user.getId()),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole().name()
            );
        }
    }

    @PostMapping("/login")
    public Map<String, UserView> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        AppUser user;
        try {
            user = authService.authenticate(request.username(), request.password());
        } catch (ApiException error) {
            authAuditService.record(
                    userRepository.findByUsername(request.username()).map(AppUser::getId).orElse(null),
                    request.username(), false, httpRequest.getRemoteAddr());
            throw error;
        }
        authAuditService.record(user.getId(), user.getUsername(), true, httpRequest.getRemoteAddr());
        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            httpRequest.changeSessionId();
        }
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name().toUpperCase())
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getId().toString(), null, authorities
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("USER_ID", user.getId().toString());
        session.setAttribute("USERNAME", user.getUsername());
        session.setAttribute("ROLE", user.getRole().name());
        return Map.of("user", UserView.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    public UserView me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        AppUser user = authService.requireUserById(auth.getPrincipal().toString());
        return UserView.from(user);
    }
}
