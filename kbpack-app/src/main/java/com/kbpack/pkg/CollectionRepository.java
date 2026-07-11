package com.kbpack.pkg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<CollectionEntity, UUID> {
    List<CollectionEntity> findAllByOrderBySortOrderAscCreatedAtAsc();
}
