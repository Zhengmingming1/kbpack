package com.kbpack.pkg;

import com.kbpack.common.id.UuidV7JpaGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "package_version")
public class PackageVersion {

    public enum ParseStatus { pending, processing, success, failed }

    @Id
    @GeneratedValue(generator = UuidV7JpaGenerator.NAME)
    @GenericGenerator(name = UuidV7JpaGenerator.NAME, strategy = UuidV7JpaGenerator.STRATEGY)
    private UUID id;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "original_filename", nullable = false, length = 512)
    private String originalFilename;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "entry_file", length = 512)
    private String entryFile;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "unpacked_size")
    private Long unpackedSize;

    @Column(name = "file_count")
    private Integer fileCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 16)
    private ParseStatus parseStatus = ParseStatus.pending;

    @Column(name = "parse_error", columnDefinition = "text")
    private String parseError;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPackageId() { return packageId; }
    public void setPackageId(UUID packageId) { this.packageId = packageId; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getEntryFile() { return entryFile; }
    public void setEntryFile(String entryFile) { this.entryFile = entryFile; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public Long getUnpackedSize() { return unpackedSize; }
    public void setUnpackedSize(Long unpackedSize) { this.unpackedSize = unpackedSize; }
    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
    public ParseStatus getParseStatus() { return parseStatus; }
    public void setParseStatus(ParseStatus parseStatus) { this.parseStatus = parseStatus; }
    public String getParseError() { return parseError; }
    public void setParseError(String parseError) { this.parseError = parseError; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public long getLockVersion() { return lockVersion; }
}
