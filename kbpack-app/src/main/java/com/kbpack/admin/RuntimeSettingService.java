package com.kbpack.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbpack.common.config.KbpackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads settings that administrators can change without restarting the application. */
@Service
public class RuntimeSettingService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSettingService.class);
    private final SystemSettingRepository repository;
    private final KbpackProperties properties;

    public RuntimeSettingService(SystemSettingRepository repository, KbpackProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public int cleanupRetentionDays() {
        return positiveInt("cleanup.soft_delete_retention_days", 30, 3_650);
    }

    @Transactional(readOnly = true)
    public int taskPollIntervalSeconds() {
        return positiveInt("task.poll_interval_seconds",
                Math.max(1, Math.toIntExact(properties.getTask().getPollIntervalMs() / 1_000)), 86_400);
    }

    @Transactional(readOnly = true)
    public int taskThreadPoolSize() {
        return positiveInt("task.thread_pool_size", Math.max(1, properties.getTask().getThreadPoolSize()), 64);
    }

    @Transactional(readOnly = true)
    public long previewTicketTtlSeconds() {
        return positiveLong("preview.ticket_ttl_seconds", properties.getPreview().getTicketTtlSeconds(), 3_600);
    }

    @Transactional(readOnly = true)
    public long previewSessionTtlSeconds() {
        return positiveLong("preview.session_ttl_seconds", properties.getPreview().getSessionTtlSeconds(), 86_400);
    }

    private int positiveInt(String key, int fallback, int maximum) {
        long value = positiveLong(key, fallback, maximum);
        return value > Integer.MAX_VALUE ? fallback : (int) value;
    }

    private long positiveLong(String key, long fallback, long maximum) {
        try {
            JsonNode value = repository.findById(key).map(SystemSetting::getValue).orElse(null);
            if (value != null && value.isIntegralNumber() && value.canConvertToLong()
                    && value.asLong() > 0 && value.asLong() <= maximum) {
                return value.asLong();
            }
        } catch (RuntimeException error) {
            log.warn("Unable to read runtime setting '{}'; using configured fallback", key, error);
        }
        return Math.min(maximum, Math.max(1, fallback));
    }
}
