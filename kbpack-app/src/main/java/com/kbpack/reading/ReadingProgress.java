package com.kbpack.reading;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reading_progress")
public class ReadingProgress {

    @EmbeddedId
    private ReadingProgressId id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(length = 256)
    private String anchor;

    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent;

    @Column(name = "scroll_offset", nullable = false)
    private long scrollOffset;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReadingProgress() {
    }

    public ReadingProgress(ReadingProgressId id) {
        this.id = id;
    }

    public void update(
            UUID versionId,
            UUID packageId,
            String anchor,
            BigDecimal progressPercent,
            long scrollOffset,
            Instant updatedAt
    ) {
        this.versionId = versionId;
        this.packageId = packageId;
        this.anchor = anchor;
        this.progressPercent = progressPercent;
        this.scrollOffset = scrollOffset;
        this.updatedAt = updatedAt;
    }

    public ReadingProgressId getId() { return id; }
    public UUID getPackageId() { return packageId; }
    public UUID getVersionId() { return versionId; }
    public String getAnchor() { return anchor; }
    public BigDecimal getProgressPercent() { return progressPercent; }
    public long getScrollOffset() { return scrollOffset; }
    public Instant getUpdatedAt() { return updatedAt; }
}
