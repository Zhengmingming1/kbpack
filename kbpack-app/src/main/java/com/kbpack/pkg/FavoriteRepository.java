package com.kbpack.pkg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

    List<Favorite> findAllByIdUserIdAndIdPackageIdIn(UUID userId, Collection<UUID> packageIds);

    boolean existsByIdUserIdAndIdPackageId(UUID userId, UUID packageId);

    void deleteByIdUserIdAndIdPackageId(UUID userId, UUID packageId);
}
