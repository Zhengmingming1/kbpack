package com.kbpack.pkg;

import com.kbpack.common.id.UuidV7JpaGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "knowledge_package")
public class KnowledgePackage {

    public enum SourceType { ai, local, web_archive, manual }
    public enum Status { draft, active, deprecated, archived }

    @Id
    @GeneratedValue(generator = UuidV7JpaGenerator.NAME)
    @GenericGenerator(name = UuidV7JpaGenerator.NAME, strategy = UuidV7JpaGenerator.STRATEGY)
    private UUID id;

    @Column(nullable = false, length = 256)
    private String title;

    @Column(nullable = false, unique = true, length = 256)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "cover_asset_path", length = 512)
    private String coverAssetPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType = SourceType.manual;

    @Column(name = "source_name", length = 64)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.draft;

    @Column(nullable = false, length = 16)
    private String visibility = "private";

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_meta", columnDefinition = "jsonb")
    private Map<String, Object> qualityMeta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverAssetPath() { return coverAssetPath; }
    public void setCoverAssetPath(String coverAssetPath) { this.coverAssetPath = coverAssetPath; }
    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(UUID currentVersionId) { this.currentVersionId = currentVersionId; }
    public Map<String, Object> getQualityMeta() { return qualityMeta; }
    public void setQualityMeta(Map<String, Object> qualityMeta) { this.qualityMeta = qualityMeta; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public long getLockVersion() { return lockVersion; }
}
