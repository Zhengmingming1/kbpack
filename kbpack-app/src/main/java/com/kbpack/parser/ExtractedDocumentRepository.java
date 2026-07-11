package com.kbpack.parser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExtractedDocumentRepository extends JpaRepository<ExtractedDocument, UUID> {
    List<ExtractedDocument> findByVersionIdOrderByOrderNoAsc(UUID versionId);
    long countByPackageId(UUID packageId);
    void deleteByVersionId(UUID versionId);
}
