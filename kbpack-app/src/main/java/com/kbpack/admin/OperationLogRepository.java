package com.kbpack.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OperationLogRepository extends JpaRepository<OperationLog, UUID> {
}
