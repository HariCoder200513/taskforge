package io.taskforge.audit;

import io.taskforge.domain.AuditLog;
import io.taskforge.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Writes append-only audit records. Each write runs in its own transaction so
 * an audit entry (e.g. a failed login) persists independently of the caller.
 * Never pass passwords, tokens, or hashes in {@code metadata}.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, UUID userId, String resourceType, String resourceId,
                       String ip, String requestId, Map<String, Object> metadata) {
        repository.save(new AuditLog(userId, action, resourceType, resourceId, ip, requestId, metadata));
    }

    public void record(String action, UUID userId, String ip, String requestId) {
        record(action, userId, null, null, ip, requestId, null);
    }
}
