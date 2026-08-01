package com.kbpack.reading;

import com.kbpack.common.id.UuidV7JpaGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reading_bookmark")
public class ReadingBookmark {

    @Id
    @GeneratedValue(generator = UuidV7JpaGenerator.NAME)
    @GenericGenerator(name = UuidV7JpaGenerator.NAME, strategy = UuidV7JpaGenerator.STRATEGY)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false, length = 256)
    private String anchor;

    @Column(length = 256)
    private String label;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReadingBookmark() {
    }

    public ReadingBookmark(
            UUID userId,
            UUID packageId,
            UUID versionId,
            UUID documentId,
            String anchor,
            String label,
            String note,
            Instant now
    ) {
        this.userId = userId;
        this.packageId = packageId;
        this.versionId = versionId;
        this.documentId = documentId;
        this.anchor = anchor;
        this.label = label;
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(boolean labelPresent, String label, boolean notePresent, String note, Instant now) {
        if (labelPresent) {
            this.label = label;
        }
        if (notePresent) {
            this.note = note;
        }
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public UUID getPackageId() { return packageId; }
    public UUID getVersionId() { return versionId; }
    public UUID getDocumentId() { return documentId; }
    public String getAnchor() { return anchor; }
    public String getLabel() { return label; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
