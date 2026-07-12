package com.kbpack.pkg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbpack.admin.SystemSetting;
import com.kbpack.admin.SystemSettingRepository;
import com.kbpack.common.config.KbpackProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadLimitServiceTest {

    @Test
    void fallsBackInsteadOfTruncatingPersistedIntegerOutsideLongRange() throws Exception {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        MultipartProperties multipart = mock(MultipartProperties.class);
        SystemSetting packageLimit = new SystemSetting();
        packageLimit.setKey("upload.max_package_size_mb");
        packageLimit.setValue(new ObjectMapper().readTree("9223372036854775808"));
        when(repository.findAllById(any())).thenReturn(List.of(packageLimit));
        when(multipart.getMaxFileSize()).thenReturn(DataSize.ofGigabytes(5));
        when(multipart.getMaxRequestSize()).thenReturn(DataSize.ofGigabytes(6));
        UploadLimitService service = new UploadLimitService(repository, new KbpackProperties(), multipart);

        UploadLimitService.Limits limits = service.current();

        assertThat(limits.maxPackageSizeBytes()).isEqualTo(500L * 1024 * 1024);
    }
}
