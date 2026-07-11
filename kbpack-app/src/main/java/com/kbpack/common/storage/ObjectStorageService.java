package com.kbpack.common.storage;

import com.kbpack.common.config.KbpackProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ObjectStorageService {

    private final MinioClient client;
    private final KbpackProperties properties;

    public ObjectStorageService(MinioClient client, KbpackProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public String originalBucket() {
        return properties.getStorage().getMinio().getBuckets().getOriginal();
    }

    public String packagesBucket() {
        return properties.getStorage().getMinio().getBuckets().getPackages();
    }

    public void put(String bucket, String key, InputStream input, long size, String contentType) {
        try (input) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(input, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to store object " + key, e);
        }
    }

    public void putBytes(String bucket, String key, byte[] data, String contentType) {
        put(bucket, key, new ByteArrayInputStream(data), data.length, contentType);
    }

    public GetObjectResponse open(String bucket, String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Failed to read object " + key, e);
        }
    }

    public byte[] readBytes(String bucket, String key, long maxBytes) {
        try (InputStream input = open(bucket, key)) {
            return readLimited(input, maxBytes);
        } catch (IOException e) {
            throw new StorageException("Failed to read object " + key, e);
        }
    }

    public void remove(String bucket, String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Failed to remove object " + key, e);
        }
    }

    static byte[] readLimited(InputStream input, long maxBytes) throws IOException {
        var output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Object exceeds configured read limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) { super(message, cause); }
    }
}
