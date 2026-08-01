package com.kbpack.reading;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReadingBookmarkRepository extends JpaRepository<ReadingBookmark, UUID> {

    boolean existsByUserIdAndDocumentIdAndAnchor(UUID userId, UUID documentId, String anchor);

    Optional<ReadingBookmark> findByUserIdAndDocumentIdAndAnchor(UUID userId, UUID documentId, String anchor);

    void deleteByUserIdAndDocumentIdAndAnchor(UUID userId, UUID documentId, String anchor);

    Optional<ReadingBookmark> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
            select b from ReadingBookmark b
            join KnowledgePackage p on p.id = b.packageId
            join PackageVersion v on v.id = b.versionId
            where b.userId = :userId
              and (:packageId is null or b.packageId = :packageId)
              and p.deletedAt is null
              and v.deletedAt is null
              and (:administrator = true or p.ownerId = :userId or p.visibility in ('team', 'public'))
            """, countQuery = """
            select count(b) from ReadingBookmark b
            join KnowledgePackage p on p.id = b.packageId
            join PackageVersion v on v.id = b.versionId
            where b.userId = :userId
              and (:packageId is null or b.packageId = :packageId)
              and p.deletedAt is null
              and v.deletedAt is null
              and (:administrator = true or p.ownerId = :userId or p.visibility in ('team', 'public'))
            """)
    Page<ReadingBookmark> findAccessible(
            @Param("userId") UUID userId,
            @Param("packageId") UUID packageId,
            @Param("administrator") boolean administrator,
            Pageable pageable
    );
}
