package com.kbpack.parser;

import com.kbpack.common.id.UuidV7JpaGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@Entity
@Table(name = "search_chunk")
public class SearchChunk {
    @Id
    @GeneratedValue(generator = UuidV7JpaGenerator.NAME)
    @GenericGenerator(name = UuidV7JpaGenerator.NAME, strategy = UuidV7JpaGenerator.STRATEGY)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;
    @Column(name = "package_id", nullable = false)
    private UUID packageId;
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;
    @Column(length = 512)
    private String heading;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "token_count")
    private Integer tokenCount;
    @Column(length = 256)
    private String anchor;

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }
    public UUID getPackageId() { return packageId; }
    public void setPackageId(UUID packageId) { this.packageId = packageId; }
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public String getAnchor() { return anchor; }
    public void setAnchor(String anchor) { this.anchor = anchor; }
}
