package com.kbpack.reading;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ReadingProgressId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    protected ReadingProgressId() {
    }

    public ReadingProgressId(UUID userId, UUID documentId) {
        this.userId = userId;
        this.documentId = documentId;
    }

    public UUID getUserId() { return userId; }
    public UUID getDocumentId() { return documentId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReadingProgressId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, documentId);
    }
}
