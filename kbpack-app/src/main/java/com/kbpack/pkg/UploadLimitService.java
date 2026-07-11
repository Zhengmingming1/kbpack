package com.kbpack.pkg;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbpack.admin.SystemSetting;
import com.kbpack.admin.SystemSettingRepository;
import com.kbpack.common.config.KbpackProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UploadLimitService {
    private static final long MB = 1024L * 1024;
    private static final List<String> KEYS = List.of(
            "upload.max_package_size_mb",
            "upload.max_unpacked_size_mb",
            "upload.max_file_count",
            "upload.max_single_file_size_mb",
            "upload.max_path_length"
    );
    private final SystemSettingRepository settingRepository;
    private final KbpackProperties properties;

    public UploadLimitService(SystemSettingRepository settingRepository, KbpackProperties properties) {
        this.settingRepository = settingRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Limits current() {
        Map<String, JsonNode> values = new HashMap<>();
        for (SystemSetting setting : settingRepository.findAllById(KEYS)) {
            values.put(setting.getKey(), setting.getValue());
        }
        KbpackProperties.Upload fallback = properties.getUpload();
        return new Limits(
                positive(values, "upload.max_package_size_mb", fallback.getMaxPackageSizeBytes() / MB) * MB,
                positive(values, "upload.max_unpacked_size_mb", fallback.getMaxUnpackedSizeBytes() / MB) * MB,
                Math.toIntExact(positive(values, "upload.max_file_count", fallback.getMaxFileCount())),
                positive(values, "upload.max_single_file_size_mb", fallback.getMaxSingleFileSizeBytes() / MB) * MB,
                Math.toIntExact(positive(values, "upload.max_path_length", fallback.getMaxPathLength()))
        );
    }

    private long positive(Map<String, JsonNode> values, String key, long fallback) {
        JsonNode value = values.get(key);
        long number = value == null ? fallback : value.asLong(fallback);
        return number > 0 ? number : fallback;
    }

    public record Limits(
            long maxPackageSizeBytes,
            long maxUnpackedSizeBytes,
            int maxFileCount,
            long maxSingleFileSizeBytes,
            int maxPathLength
    ) {}
}
