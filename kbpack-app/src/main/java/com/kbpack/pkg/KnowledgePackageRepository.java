package com.kbpack.pkg;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgePackageRepository extends JpaRepository<KnowledgePackage, UUID>,
        JpaSpecificationExecutor<KnowledgePackage> {

    @Query("select p from KnowledgePackage p where p.deletedAt is null")
    Page<KnowledgePackage> findAllActive(Pageable pageable);

    @Query("select p from KnowledgePackage p where p.id = :id and p.deletedAt is null")
    Optional<KnowledgePackage> findActiveById(@Param("id") UUID id);

    boolean existsBySlug(String slug);

    @Query("select count(p) from KnowledgePackage p where p.deletedAt is null")
    long countActive();
}
