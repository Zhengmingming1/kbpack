package com.kbpack.common.storage;

import com.kbpack.common.config.KbpackProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    MinioClient minioClient(KbpackProperties properties) {
        var minio = properties.getStorage().getMinio();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    @Bean
    @Order(20)
    ApplicationRunner minioBucketInitializer(MinioClient minioClient, KbpackProperties properties) {
        return args -> {
            for (String bucket : properties.getStorage().getMinio().getBuckets().all()) {
                try {
                    boolean exists = minioClient.bucketExists(
                            BucketExistsArgs.builder().bucket(bucket).build()
                    );
                    if (!exists) {
                        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                        log.info("Created MinIO bucket: {}", bucket);
                    } else {
                        log.debug("MinIO bucket exists: {}", bucket);
                    }
                } catch (Exception e) {
                    // Do not fail app startup hard during early phases; log for operator.
                    log.error("Failed to ensure MinIO bucket {}: {}", bucket, e.getMessage());
                }
            }
        };
    }
}
