package com.kbpack.reading;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recent_view")
public class RecentView {

    @EmbeddedId
    private RecentViewId id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    protected RecentView() {
    }

    public RecentView(RecentViewId id) {
        this.id = id;
    }

    public void update(UUID packageId, UUID versionId, Instant viewedAt) {
        this.packageId = packageId;
        this.versionId = versionId;
        this.viewedAt = viewedAt;
    }

    public RecentViewId getId() { return id; }
    public UUID getPackageId() { return packageId; }
    public UUID getVersionId() { return versionId; }
    public Instant getViewedAt() { return viewedAt; }
}
