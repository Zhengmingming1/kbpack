package com.kbpack.user;

import com.kbpack.admin.OperationLog;
import com.kbpack.admin.OperationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthAuditService {
    private final OperationLogRepository logRepository;

    public AuthAuditService(OperationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String username, boolean success, String ip) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setAction(success ? "auth.login.success" : "auth.login.failed");
        log.setTargetType("app_user");
        log.setTargetId(userId);
        log.setDetail(Map.of("username", username == null ? "" : username));
        log.setIp(ip);
        logRepository.save(log);
    }
}
