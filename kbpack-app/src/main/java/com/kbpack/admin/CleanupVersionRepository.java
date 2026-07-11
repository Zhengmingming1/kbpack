package com.kbpack.admin;

import com.kbpack.pkg.PackageVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface CleanupVersionRepository extends JpaRepository<PackageVersion, UUID> {
    List<PackageVersion> findByPackageId(UUID packageId);
    List<PackageVersion> findByDeletedAtBefore(Instant cutoff);
}
