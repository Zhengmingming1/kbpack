package com.kbpack.admin;

import com.kbpack.pkg.KnowledgePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CleanupPackageRepository extends JpaRepository<KnowledgePackage, UUID> {
    List<KnowledgePackage> findByDeletedAtBefore(Instant cutoff);
}
