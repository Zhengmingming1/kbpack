package com.kbpack.parser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SearchChunkRepository extends JpaRepository<SearchChunk, UUID> {
    List<SearchChunk> findByPackageId(UUID packageId);
    List<SearchChunk> findByVersionId(UUID versionId);
    void deleteByVersionId(UUID versionId);
}
