package io.taskforge.repository;

import io.taskforge.domain.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueRepository extends JpaRepository<Queue, UUID> {
    boolean existsByName(String name);

    Optional<Queue> findByName(String name);

    /** Queues a non-admin user may see: owned, or holding any permission on them. */
    @Query("select q from Queue q where q.ownerId = :uid or q.id in "
            + "(select p.id.queueId from QueuePermission p where p.id.userId = :uid) "
            + "order by q.createdAt desc")
    List<Queue> findVisibleTo(@Param("uid") UUID uid);
}
