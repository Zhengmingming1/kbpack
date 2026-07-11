package com.kbpack.pkg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackageAssetRepository extends JpaRepository<PackageAsset, UUID> {
    List<PackageAsset> findByVersionIdOrderByPathAsc(UUID versionId);
    Optional<PackageAsset> findByVersionIdAndPath(UUID versionId, String path);
    void deleteByVersionId(UUID versionId);
}
