package com.kbpack.admin;

import com.kbpack.common.config.KbpackProperties;
import com.meilisearch.sdk.Client;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final Client meilisearchClient;
    private final MinioClient minioClient;
    private final KbpackProperties properties;

    public HealthController(
            JdbcTemplate jdbcTemplate,
            Client meilisearchClient,
            MinioClient minioClient,
            KbpackProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.meilisearchClient = meilisearchClient;
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> health() {
        return Map.of("status", "up");
    }

    @GetMapping(value = "/health/db", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> database() {
        return probe("database", () -> {
            Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);
            if (value == null || value != 1) {
                throw new IllegalStateException("unexpected probe result");
            }
        });
    }

    @GetMapping(value = "/health/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> search() {
        return probe("search", () -> {
            if (!Boolean.TRUE.equals(meilisearchClient.isHealthy())) {
                throw new IllegalStateException("service reported unhealthy");
            }
        });
    }

    @GetMapping(value = "/health/storage", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> storage() {
        return probe("storage", () -> {
            for (String bucket : properties.getStorage().getMinio().getBuckets().all()) {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build()
                );
                if (!exists) {
                    throw new IllegalStateException("required bucket is missing: " + bucket);
                }
            }
        });
    }

    private ResponseEntity<Map<String, Object>> probe(String dependency, Probe probe) {
        try {
            probe.check();
            return ResponseEntity.ok(Map.of("status", "up"));
        } catch (Exception ex) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "down");
            body.put("detail", dependency + " unavailable: " + safeMessage(ex));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    @FunctionalInterface
    private interface Probe {
        void check() throws Exception;
    }
}
