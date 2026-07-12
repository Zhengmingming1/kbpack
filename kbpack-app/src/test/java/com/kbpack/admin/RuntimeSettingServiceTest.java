package com.kbpack.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.common.config.KbpackProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeSettingServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readsCurrentDatabaseValueOnEveryCall() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        RuntimeSettingService service = new RuntimeSettingService(repository, new KbpackProperties());
        SystemSetting first = setting("task.thread_pool_size", 3);
        SystemSetting second = setting("task.thread_pool_size", 5);
        when(repository.findById("task.thread_pool_size"))
                .thenReturn(Optional.of(first), Optional.of(second));

        assertThat(service.taskThreadPoolSize()).isEqualTo(3);
        assertThat(service.taskThreadPoolSize()).isEqualTo(5);
    }

    @Test
    void rejectsUnsafePersistedValueAndUsesFallback() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        KbpackProperties properties = new KbpackProperties();
        properties.getTask().setThreadPoolSize(2);
        RuntimeSettingService service = new RuntimeSettingService(repository, properties);
        when(repository.findById("task.thread_pool_size"))
                .thenReturn(Optional.of(setting("task.thread_pool_size", 1_000_000)));

        assertThat(service.taskThreadPoolSize()).isEqualTo(2);
    }

    private static SystemSetting setting(String key, int value) {
        SystemSetting setting = new SystemSetting();
        setting.setKey(key);
        setting.setValue(JSON.valueToTree(value));
        return setting;
    }
}
