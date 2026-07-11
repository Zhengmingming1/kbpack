package com.kbpack.admin;

import com.kbpack.common.config.KbpackProperties;
import com.meilisearch.sdk.Client;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private Client meilisearchClient;
    @Mock private MinioClient minioClient;

    @Test
    void databaseProbeReportsUp() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);
        HealthController controller = controller();

        var response = controller.database();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "up");
    }

    @Test
    void storageProbeReportsDownWhenBucketIsMissing() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        HealthController controller = controller();

        var response = controller.storage();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "down");
    }

    private HealthController controller() {
        return new HealthController(
                jdbcTemplate,
                meilisearchClient,
                minioClient,
                new KbpackProperties()
        );
    }
}
