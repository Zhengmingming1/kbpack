package com.kbpack.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class WebConfig {

    public static final String TRACE_HEADER = "X-Trace-Id";

    @Bean
    OncePerRequestFilter traceIdFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                String traceId = request.getHeader(TRACE_HEADER);
                if (traceId == null || traceId.isBlank()) {
                    traceId = UUID.randomUUID().toString().replace("-", "");
                }
                MDC.put("traceId", traceId);
                response.setHeader(TRACE_HEADER, traceId);
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    MDC.remove("traceId");
                }
            }
        };
    }
}
