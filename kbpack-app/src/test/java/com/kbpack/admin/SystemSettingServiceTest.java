package com.kbpack.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private SystemSettingRepository repository;
    @Mock private OperationLogService operationLogService;
    @Mock private MultipartProperties multipartProperties;

    @InjectMocks
    private SystemSettingService service;

    @Test
    void rejectsUnknownSettingKey() {
        assertThatThrownBy(() -> service.patch(
                Map.of("unknown", JSON.valueToTree(1)), actor(), "127.0.0.1"
        )).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void updatesKnownPositiveNumericSetting() {
        SystemSetting setting = new SystemSetting();
        setting.setKey("task.thread_pool_size");
        setting.setValue(JSON.valueToTree(2));
        when(repository.findAllById(any())).thenReturn(List.of(setting));
        when(repository.findAllByOrderByKeyAsc()).thenReturn(List.of(setting));

        Map<String, ?> result = service.patch(
                Map.of("task.thread_pool_size", JSON.valueToTree(4)),
                actor(),
                "127.0.0.1"
        );

        assertThat(result.get("task.thread_pool_size").toString()).isEqualTo("4");
        verify(repository).saveAll(any());
    }

    @Test
    void rejectsUnsafeThreadPoolSize() {
        SystemSetting setting = setting("task.thread_pool_size", 2);
        when(repository.findAllById(any())).thenReturn(List.of(setting));

        assertThatThrownBy(() -> service.patch(
                Map.of("task.thread_pool_size", JSON.valueToTree(65)), actor(), "127.0.0.1"
        )).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void rejectsPackageLimitAboveMultipartTransportCeiling() {
        SystemSetting setting = setting("upload.max_package_size_mb", 500);
        when(repository.findAllById(any())).thenReturn(List.of(setting));
        when(multipartProperties.getMaxFileSize()).thenReturn(DataSize.ofGigabytes(5));
        when(multipartProperties.getMaxRequestSize()).thenReturn(DataSize.ofGigabytes(6));

        assertThatThrownBy(() -> service.patch(
                Map.of("upload.max_package_size_mb", JSON.valueToTree(5_121)), actor(), "127.0.0.1"
        )).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void rejectsIntegralValueOutsideLongRange() throws Exception {
        SystemSetting setting = setting("task.thread_pool_size", 2);
        when(repository.findAllById(any())).thenReturn(List.of(setting));

        assertThatThrownBy(() -> service.patch(
                Map.of("task.thread_pool_size", JSON.readTree("9223372036854775808")),
                actor(), "127.0.0.1"
        )).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    private SystemSetting setting(String key, int value) {
        SystemSetting setting = new SystemSetting();
        setting.setKey(key);
        setting.setValue(JSON.valueToTree(value));
        return setting;
    }

    private AppUser actor() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setRole(AppUser.Role.owner);
        return user;
    }
}
