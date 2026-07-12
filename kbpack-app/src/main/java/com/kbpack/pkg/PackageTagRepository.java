package com.kbpack.pkg;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PackageTagRepository extends JpaRepository<PackageTag, PackageTagId> {

    List<PackageTag> findAllByIdPackageId(UUID packageId);

    List<PackageTag> findAllByIdPackageIdIn(Collection<UUID> packageIds);

    List<PackageTag> findAllByIdTagId(UUID tagId);

    boolean existsByIdPackageIdAndIdTagId(UUID packageId, UUID tagId);

    long countByIdTagId(UUID tagId);

    @Query("select count(link) from PackageTag link, KnowledgePackage pkg " +
            "where link.id.tagId = :tagId and link.id.packageId = pkg.id and pkg.deletedAt is null")
    long countActiveByTagId(@Param("tagId") UUID tagId);

    void deleteByIdPackageIdAndIdTagId(UUID packageId, UUID tagId);
}
