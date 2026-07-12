package com.kbpack.pkg;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbpack.admin.SystemSetting;
import com.kbpack.admin.SystemSettingRepository;
import com.kbpack.common.config.KbpackProperties;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UploadLimitService {
    private static final long MB = 1024L * 1024;
    private static final long DEFAULT_TRANSPORT_MAXIMUM_MB = 5_120;
    private static final List<String> KEYS = List.of(
            "upload.max_package_size_mb",
            "upload.max_unpacked_size_mb",
            "upload.max_file_count",
            "upload.max_single_file_size_mb",
            "upload.max_path_length"
    );
    private final SystemSettingRepository settingRepository;
    private final KbpackProperties properties;
    private final MultipartProperties multipartProperties;

    public UploadLimitService(
            SystemSettingRepository settingRepository,
            KbpackProperties properties,
            MultipartProperties multipartProperties
    ) {
        this.settingRepository = settingRepository;
        this.properties = properties;
        this.multipartProperties = multipartProperties;
    }

    @Transactional(readOnly = true)
    public Limits current() {
        Map<String, JsonNode> values = new HashMap<>();
        for (SystemSetting setting : settingRepository.findAllById(KEYS)) {
            values.put(setting.getKey(), setting.getValue());
        }
        KbpackProperties.Upload fallback = properties.getUpload();
        long transportMaximumMb = multipartTransportMaximumMb();
        return new Limits(
                bounded(values, "upload.max_package_size_mb", fallback.getMaxPackageSizeBytes() / MB,
                        transportMaximumMb) * MB,
                bounded(values, "upload.max_unpacked_size_mb", fallback.getMaxUnpackedSizeBytes() / MB,
                        51_200) * MB,
                Math.toIntExact(bounded(values, "upload.max_file_count", fallback.getMaxFileCount(), 100_000)),
                bounded(values, "upload.max_single_file_size_mb", fallback.getMaxSingleFileSizeBytes() / MB,
                        transportMaximumMb) * MB,
                Math.toIntExact(bounded(values, "upload.max_path_length", fallback.getMaxPathLength(), 512))
        );
    }

    private long bounded(Map<String, JsonNode> values, String key, long fallback, long maximum) {
        long safeFallback = Math.min(Math.max(1, fallback), maximum);
        JsonNode value = values.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            return safeFallback;
        }
        long number = value.asLong();
        return number > 0 && number <= maximum ? number : safeFallback;
    }

    private long multipartTransportMaximumMb() {
        long fileBytes = multipartProperties.getMaxFileSize().toBytes();
        long requestBytes = multipartProperties.getMaxRequestSize().toBytes();
        long fileMaximum = fileBytes > 0 ? Math.max(1, fileBytes / MB) : DEFAULT_TRANSPORT_MAXIMUM_MB;
        long requestMaximum = requestBytes > MB
                ? Math.max(1, (requestBytes - MB) / MB)
                : DEFAULT_TRANSPORT_MAXIMUM_MB;
        return Math.min(fileMaximum, requestMaximum);
    }

    public record Limits(
            long maxPackageSizeBytes,
            long maxUnpackedSizeBytes,
            int maxFileCount,
            long maxSingleFileSizeBytes,
            int maxPathLength
    ) {}
}
