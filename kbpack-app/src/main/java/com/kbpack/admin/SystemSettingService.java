package com.kbpack.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSettingService {

    private static final long DEFAULT_TRANSPORT_MAXIMUM_MB = 5_120;
    private static final Map<String, Long> MAX_VALUES = Map.of(
            "upload.max_unpacked_size_mb", 51_200L,
            "upload.max_file_count", 100_000L,
            "upload.max_path_length", 512L,
            "cleanup.soft_delete_retention_days", 3_650L,
            "task.poll_interval_seconds", 86_400L,
            "task.thread_pool_size", 64L,
            "preview.ticket_ttl_seconds", 3_600L,
            "preview.session_ttl_seconds", 86_400L
    );

    private final SystemSettingRepository repository;
    private final OperationLogService operationLogService;
    private final MultipartProperties multipartProperties;

    public SystemSettingService(
            SystemSettingRepository repository,
            OperationLogService operationLogService,
            MultipartProperties multipartProperties
    ) {
        this.repository = repository;
        this.operationLogService = operationLogService;
        this.multipartProperties = multipartProperties;
    }

    @Transactional(readOnly = true)
    public Map<String, JsonNode> getAll() {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        repository.findAllByOrderByKeyAsc().forEach(setting -> result.put(setting.getKey(), setting.getValue()));
        return result;
    }

    @Transactional
    public Map<String, JsonNode> patch(
            Map<String, JsonNode> updates,
            AppUser actor,
            String ip
    ) {
        if (updates == null || updates.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "设置内容不能为空");
        }
        Map<String, SystemSetting> existing = new LinkedHashMap<>();
        repository.findAllById(updates.keySet()).forEach(setting -> existing.put(setting.getKey(), setting));
        List<String> unknownKeys = updates.keySet().stream().filter(key -> !existing.containsKey(key)).sorted().toList();
        if (!unknownKeys.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "未知设置项: " + String.join(", ", unknownKeys));
        }

        updates.forEach((key, value) -> {
            validateValue(key, value);
            existing.get(key).setValue(value);
        });
        repository.saveAll(existing.values());
        operationLogService.record(
                actor.getId(),
                "settings.update",
                "system_setting",
                null,
                Map.of("keys", updates.keySet().stream().sorted().toList()),
                ip
        );
        return getAll();
    }

    private void validateValue(String key, JsonNode value) {
        if (value == null || value.isNull()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, key + " 不能为空");
        }
        if ((key.startsWith("upload.") || key.startsWith("cleanup.")
                || key.startsWith("task.") || key.startsWith("preview."))
                && (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, key + " 必须是正整数");
        }
        long number = value.asLong();
        Long maximum = MAX_VALUES.get(key);
        if (maximum != null && number > maximum) {
            throw new ApiException(ErrorCode.BAD_REQUEST, key + " must not exceed " + maximum);
        }
        if ("upload.max_package_size_mb".equals(key)
                || "upload.max_single_file_size_mb".equals(key)) {
            long transportMaximumMb = multipartTransportMaximumMb();
            if (number > transportMaximumMb) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        key + " must not exceed multipart transport ceiling " + transportMaximumMb + " MB");
            }
        }
    }

    private long multipartTransportMaximumMb() {
        long mb = 1024L * 1024;
        long fileBytes = multipartProperties.getMaxFileSize().toBytes();
        long requestBytes = multipartProperties.getMaxRequestSize().toBytes();
        long fileMaximum = fileBytes > 0 ? Math.max(1, fileBytes / mb) : DEFAULT_TRANSPORT_MAXIMUM_MB;
        long requestMaximum = requestBytes > mb
                ? Math.max(1, (requestBytes - mb) / mb)
                : DEFAULT_TRANSPORT_MAXIMUM_MB;
        return Math.min(fileMaximum, requestMaximum);
    }
}
