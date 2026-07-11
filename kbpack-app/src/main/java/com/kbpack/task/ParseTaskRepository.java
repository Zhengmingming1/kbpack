package com.kbpack.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ParseTaskRepository extends JpaRepository<ParseTask, UUID>, JpaSpecificationExecutor<ParseTask> {
    Page<ParseTask> findByStatus(ParseTask.Status status, Pageable pageable);
    Page<ParseTask> findByVersionId(UUID versionId, Pageable pageable);
    Page<ParseTask> findByStatusAndVersionId(ParseTask.Status status, UUID versionId, Pageable pageable);
    long countByStatus(ParseTask.Status status);
    List<ParseTask> findAllByStatus(ParseTask.Status status);
    boolean existsByVersionIdAndStatusIn(UUID versionId, List<ParseTask.Status> statuses);

    @Query(value = "select task.* from parse_task task " +
            "join package_version version on version.id = task.version_id " +
            "join knowledge_package pkg on pkg.id = version.package_id " +
            "where version.deleted_at is null and pkg.deleted_at is null and " +
            "(task.status = 'pending' or " +
            "(task.status = 'retry_scheduled' and task.next_retry_at <= :now)) " +
            "order by task.created_at asc for update of task skip locked limit :limit", nativeQuery = true)
    List<ParseTask> findRunnable(@Param("now") Instant now, @Param("limit") int limit);
}
