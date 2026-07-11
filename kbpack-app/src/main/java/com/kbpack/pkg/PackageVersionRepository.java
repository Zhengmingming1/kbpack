package com.kbpack.pkg;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackageVersionRepository extends JpaRepository<PackageVersion, UUID> {

    @Query("select v from PackageVersion v where v.packageId = :packageId and v.deletedAt is null order by v.versionNo desc")
    List<PackageVersion> findActiveByPackageId(@Param("packageId") UUID packageId);

    @Query("select v from PackageVersion v where v.id = :id and v.deletedAt is null")
    Optional<PackageVersion> findActiveById(@Param("id") UUID id);

    @Query("select v from PackageVersion v where v.id = :id and v.packageId = :packageId and v.deletedAt is null")
    Optional<PackageVersion> findActiveByIdAndPackageId(
            @Param("id") UUID id,
            @Param("packageId") UUID packageId
    );

    @Query("select coalesce(max(v.versionNo), 0) from PackageVersion v where v.packageId = :packageId")
    int findMaxVersionNo(@Param("packageId") UUID packageId);
}
