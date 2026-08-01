package com.kbpack.pkg;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface PackageVersionRepository extends JpaRepository<PackageVersion, UUID> {

    @Query("select v from PackageVersion v where v.packageId = :packageId and v.deletedAt is null order by v.versionNo desc")
    List<PackageVersion> findActiveByPackageId(@Param("packageId") UUID packageId);

    @Query("select v from PackageVersion v where v.id = :id and v.deletedAt is null")
    Optional<PackageVersion> findActiveById(@Param("id") UUID id);

    @Query("select v.packageId from PackageVersion v where v.id = :id and v.deletedAt is null")
    Optional<UUID> findActivePackageIdById(@Param("id") UUID id);

    @Query("select v from PackageVersion v where v.id = :id and v.packageId = :packageId and v.deletedAt is null")
    Optional<PackageVersion> findActiveByIdAndPackageId(
            @Param("id") UUID id,
            @Param("packageId") UUID packageId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from PackageVersion v where v.id = :id and v.packageId = :packageId and v.deletedAt is null")
    Optional<PackageVersion> findActiveByIdAndPackageIdForUpdate(
            @Param("id") UUID id,
            @Param("packageId") UUID packageId
    );

    @Query("select coalesce(max(v.versionNo), 0) from PackageVersion v where v.packageId = :packageId")
    int findMaxVersionNo(@Param("packageId") UUID packageId);

    @Query("select v from PackageVersion v where v.packageId = :packageId "
            + "and v.promoteOnSuccess = true and v.deletedAt is null order by v.versionNo desc")
    List<PackageVersion> findPromotionCandidates(@Param("packageId") UUID packageId);

    @Modifying
    @Query("update PackageVersion v set v.promoteOnSuccess = false, "
            + "v.lockVersion = v.lockVersion + 1 "
            + "where v.packageId = :packageId and v.promoteOnSuccess = true")
    int clearPromotionCandidates(@Param("packageId") UUID packageId);

    @Query("select v from PackageVersion v where v.packageId = :packageId order by v.versionNo desc")
    List<PackageVersion> findAllIncludingDeletedByPackageId(@Param("packageId") UUID packageId);

    @Query("select v from PackageVersion v where v.packageId = :packageId and v.deletedAt = :deletedAt")
    List<PackageVersion> findByPackageIdAndDeletedAt(
            @Param("packageId") UUID packageId,
            @Param("deletedAt") Instant deletedAt
    );

    long countByPackageId(UUID packageId);
}
