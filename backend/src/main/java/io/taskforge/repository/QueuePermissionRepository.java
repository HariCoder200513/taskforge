package io.taskforge.repository;

import io.taskforge.domain.QueuePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface QueuePermissionRepository extends JpaRepository<QueuePermission, QueuePermission.Id> {

    @Query("select count(p) > 0 from QueuePermission p "
            + "where p.id.queueId = :queueId and p.id.userId = :userId "
            + "and p.id.permission in :permissions")
    boolean hasAnyPermission(@Param("queueId") UUID queueId,
                             @Param("userId") UUID userId,
                             @Param("permissions") Collection<String> permissions);

    @Query("select count(p) > 0 from QueuePermission p "
            + "where p.id.queueId = :queueId and p.id.userId = :userId")
    boolean hasAnyPermissionAtAll(@Param("queueId") UUID queueId, @Param("userId") UUID userId);
}
