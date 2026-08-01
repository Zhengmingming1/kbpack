package com.kbpack.reading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, ReadingProgressId> {

    @Modifying
    @Query(value = """
            insert into reading_progress (
                user_id, document_id, package_id, version_id, anchor,
                progress_percent, scroll_offset, updated_at
            ) values (
                :userId, :documentId, :packageId, :versionId, :anchor,
                :progress, :scrollOffset, :updatedAt
            )
            on conflict (user_id, document_id) do update set
                package_id = excluded.package_id,
                version_id = excluded.version_id,
                anchor = excluded.anchor,
                progress_percent = excluded.progress_percent,
                scroll_offset = excluded.scroll_offset,
                updated_at = excluded.updated_at
            """, nativeQuery = true)
    int upsert(
            @Param("userId") UUID userId,
            @Param("documentId") UUID documentId,
            @Param("packageId") UUID packageId,
            @Param("versionId") UUID versionId,
            @Param("anchor") String anchor,
            @Param("progress") BigDecimal progress,
            @Param("scrollOffset") long scrollOffset,
            @Param("updatedAt") Instant updatedAt
    );
}
