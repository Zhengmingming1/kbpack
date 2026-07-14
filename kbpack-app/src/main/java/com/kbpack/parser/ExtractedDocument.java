package com.kbpack.parser;

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
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "extracted_document")
public class ExtractedDocument {

    public enum DocType { markdown, html, content_js, text }

    @Id
    @GeneratedValue(generator = UuidV7JpaGenerator.NAME)
    @GenericGenerator(name = UuidV7JpaGenerator.NAME, strategy = UuidV7JpaGenerator.STRATEGY)
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "package_id", nullable = false)
    private UUID packageId;

    @Column(name = "source_path", nullable = false, length = 1024)
    private String sourcePath;

    @Column(length = 512)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 16)
    private DocType docType;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "content_length", nullable = false)
    private long contentLength;

    @Column(name = "raw_content", columnDefinition = "text")
    private String rawContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "heading_tree", columnDefinition = "jsonb")
    private List<Map<String, Object>> headingTree;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updateContentMetadata();
    }

    @PreUpdate
    void onUpdate() {
        updateContentMetadata();
    }

    private void updateContentMetadata() {
        if (content == null) {
            contentHash = null;
            contentLength = 0;
            return;
        }
        contentLength = content.codePointCount(0, content.length());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            contentHash = HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public UUID getId() { return id; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public UUID getPackageId() { return packageId; }
    public void setPackageId(UUID packageId) { this.packageId = packageId; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public DocType getDocType() { return docType; }
    public void setDocType(DocType docType) { this.docType = docType; }
    public int getOrderNo() { return orderNo; }
    public void setOrderNo(int orderNo) { this.orderNo = orderNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentHash() { return contentHash; }
    public long getContentLength() { return contentLength; }
    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }
    public List<Map<String, Object>> getHeadingTree() { return headingTree; }
    public void setHeadingTree(List<Map<String, Object>> headingTree) { this.headingTree = headingTree; }
    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
    public Instant getCreatedAt() { return createdAt; }
}
