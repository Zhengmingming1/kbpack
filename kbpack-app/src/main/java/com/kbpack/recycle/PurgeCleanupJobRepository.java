package com.kbpack.recycle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurgeCleanupJobRepository extends JpaRepository<PurgeCleanupJob, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from PurgeCleanupJob job where job.id = :id")
    Optional<PurgeCleanupJob> findByIdForUpdate(@Param("id") UUID id);

    List<PurgeCleanupJob> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<PurgeCleanupJob.Status> statuses,
            Instant now
    );
}
