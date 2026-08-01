package com.kbpack.recycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "purge_cleanup_job")
public class PurgeCleanupJob {

    public enum Status { pending, retry, completed }

    @Id
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "version_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> versionIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "storage_objects", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, String>> storageObjects = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PurgeCleanupJob() {
    }

    public PurgeCleanupJob(
            UUID id,
            UUID packageId,
            List<String> versionIds,
            List<Map<String, String>> storageObjects,
            Instant now
    ) {
        this.id = id;
        this.packageId = packageId;
        this.versionIds = new ArrayList<>(versionIds);
        this.storageObjects = new ArrayList<>();
        storageObjects.forEach(object -> this.storageObjects.add(new LinkedHashMap<>(object)));
        this.status = Status.pending;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markCompleted(Instant now) {
        status = Status.completed;
        completedAt = now;
        nextAttemptAt = now;
        lastError = null;
        updatedAt = now;
    }

    public void markRetry(Instant now, RuntimeException error) {
        attempts++;
        long delaySeconds = Math.min(3600L, 30L << Math.min(attempts - 1, 7));
        status = Status.retry;
        nextAttemptAt = now.plusSeconds(delaySeconds);
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        lastError = message.length() > 4000 ? message.substring(0, 4000) : message;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getPackageId() { return packageId; }
    public List<String> getVersionIds() { return versionIds; }
    public List<Map<String, String>> getStorageObjects() { return storageObjects; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
