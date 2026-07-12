package com.kbpack.pkg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PackageCollectionRepository extends JpaRepository<PackageCollection, PackageCollectionId> {

    List<PackageCollection> findAllByIdPackageId(UUID packageId);

    List<PackageCollection> findAllByIdPackageIdIn(Collection<UUID> packageIds);

    List<PackageCollection> findAllByIdCollectionId(UUID collectionId);

    List<PackageCollection> findAllByIdCollectionIdIn(Collection<UUID> collectionIds);

    boolean existsByIdPackageIdAndIdCollectionId(UUID packageId, UUID collectionId);

    void deleteByIdPackageIdAndIdCollectionId(UUID packageId, UUID collectionId);
}
