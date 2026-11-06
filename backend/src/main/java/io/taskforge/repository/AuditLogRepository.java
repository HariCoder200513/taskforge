package io.taskforge.repository;

import io.taskforge.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Append-only: the application only ever inserts (save). */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
