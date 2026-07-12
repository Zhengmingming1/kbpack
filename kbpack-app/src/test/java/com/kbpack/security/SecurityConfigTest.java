package com.kbpack.security;

import com.kbpack.common.config.KbpackProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void bindsCommaSeparatedCorsOrigins() {
        var source = new MapConfigurationPropertySource(Map.of(
                "kbpack.security.cors.allowed-origins",
                "https://kb.example.com,http://localhost:5173"
        ));

        KbpackProperties properties = new Binder(source)
                .bind("kbpack", Bindable.of(KbpackProperties.class))
                .get();

        assertThat(properties.getSecurity().getCors().getAllowedOrigins()).containsExactly(
                "https://kb.example.com",
                "http://localhost:5173"
        );
    }

    @Test
    void appliesConfiguredCorsOriginsToAllPaths() {
        KbpackProperties properties = new KbpackProperties();
        properties.getSecurity().getCors().setAllowedOrigins(List.of(
                "https://kb.example.com",
                "http://localhost:5173"
        ));

        var source = new SecurityConfig().corsConfigurationSource(properties);
        var configuration = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(
                "https://kb.example.com",
                "http://localhost:5173"
        );
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void allowsConfiguredOriginForMainAndPreviewPathsAndRejectsUnknownOrigin() throws Exception {
        KbpackProperties properties = new KbpackProperties();
        properties.getSecurity().getCors().setAllowedOrigins(List.of("https://kb.example.com"));
        var source = new SecurityConfig().corsConfigurationSource(properties);

        for (String path : List.of("/api/v1/auth/login", "/p/pkg/version/index.html")) {
            MockHttpServletRequest request = preflight(path, "https://kb.example.com");
            MockHttpServletResponse response = new MockHttpServletResponse();
            var configuration = source.getCorsConfiguration(request);

            assertThat(new DefaultCorsProcessor().processRequest(configuration, request, response)).isTrue();
            assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                    .isEqualTo("https://kb.example.com");
            assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        }

        MockHttpServletRequest request = preflight("/api/v1/auth/login", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        var configuration = source.getCorsConfiguration(request);
        assertThat(new DefaultCorsProcessor().processRequest(configuration, request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void rejectsWildcardOriginWhenCredentialsAreEnabled() {
        KbpackProperties properties = new KbpackProperties();
        properties.getSecurity().getCors().setAllowedOrigins(List.of("*"));

        assertThatThrownBy(() -> new SecurityConfig().corsConfigurationSource(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MockHttpServletRequest preflight(String path, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.OPTIONS.name(), path);
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name());
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-requested-with");
        return request;
    }
}
