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

    private AppUser actor() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setRole(AppUser.Role.owner);
        return user;
    }
}
