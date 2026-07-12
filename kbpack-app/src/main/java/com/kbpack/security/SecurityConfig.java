package com.kbpack.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.common.config.KbpackProperties;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.error.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Main-site session security. Preview host chain lands in P7.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @Order(1)
    SecurityFilterChain previewChain(HttpSecurity http, KbpackProperties properties) throws Exception {
        http
                .securityMatcher(request -> request.getRequestURI().startsWith("/p/")
                        || (properties.getPreview().isEnforceHost()
                        && previewHost(request.getServerName(), properties)))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/p/**", "/health", "/health/**").permitAll()
                        .anyRequest().denyAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    private static boolean previewHost(String host, KbpackProperties properties) {
        return host != null && host.equalsIgnoreCase(properties.getPreview().getHost());
    }

    @Bean
    @Order(2)
    SecurityFilterChain mainChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            RequestedWithFilter requestedWithFilter,
            ObjectMapper objectMapper
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/health",
                                "/health/**",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/v1/auth/login"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                                    ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getDefaultMessage(), null));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus().value());
                            response.setContentType("application/json");
                            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                                    ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getDefaultMessage(), null));
                        }))
                .addFilterBefore(requestedWithFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(KbpackProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getSecurity().getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        config.validateAllowCredentials();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
