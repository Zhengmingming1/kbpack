package com.kbpack.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSettingService {

    private final SystemSettingRepository repository;
    private final OperationLogService operationLogService;

    public SystemSettingService(
            SystemSettingRepository repository,
            OperationLogService operationLogService
    ) {
        this.repository = repository;
        this.operationLogService = operationLogService;
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
                && (!value.isIntegralNumber() || value.asLong() <= 0)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, key + " 必须是正整数");
        }
    }
}
