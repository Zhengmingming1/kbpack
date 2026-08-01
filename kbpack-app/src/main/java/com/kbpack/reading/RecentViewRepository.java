package com.kbpack.reading;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.time.Instant;

public interface RecentViewRepository extends JpaRepository<RecentView, RecentViewId> {

    @Modifying
    @Query(value = """
            insert into recent_view (user_id, document_id, package_id, version_id, viewed_at)
            values (:userId, :documentId, :packageId, :versionId, :viewedAt)
            on conflict (user_id, document_id) do update set
                package_id = excluded.package_id,
                version_id = excluded.version_id,
                viewed_at = excluded.viewed_at
            """, nativeQuery = true)
    int upsert(
            @Param("userId") UUID userId,
            @Param("documentId") UUID documentId,
            @Param("packageId") UUID packageId,
            @Param("versionId") UUID versionId,
            @Param("viewedAt") Instant viewedAt
    );

    @Query(value = """
            select r from RecentView r
            join KnowledgePackage p on p.id = r.packageId
            join PackageVersion v on v.id = r.versionId
            where r.id.userId = :userId
              and p.deletedAt is null
              and v.deletedAt is null
              and (:administrator = true or p.ownerId = :userId or p.visibility in ('team', 'public'))
            """, countQuery = """
            select count(r) from RecentView r
            join KnowledgePackage p on p.id = r.packageId
            join PackageVersion v on v.id = r.versionId
            where r.id.userId = :userId
              and p.deletedAt is null
              and v.deletedAt is null
              and (:administrator = true or p.ownerId = :userId or p.visibility in ('team', 'public'))
            """)
    Page<RecentView> findAccessible(
            @Param("userId") UUID userId,
            @Param("administrator") boolean administrator,
            Pageable pageable
    );
}
