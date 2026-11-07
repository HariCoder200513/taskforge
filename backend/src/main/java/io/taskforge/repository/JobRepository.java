package io.taskforge.repository;

import io.taskforge.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    /** Jobs a non-admin user may see: owned, in a queue they own, or in a queue
     *  they hold any permission on (see ARCHITECTURE §5.3). */
    @Query("select j from Job j where j.ownerId = :uid "
            + "or j.queueId in (select q.id from Queue q where q.ownerId = :uid) "
            + "or j.queueId in (select p.id.queueId from QueuePermission p where p.id.userId = :uid) "
            + "order by j.createdAt desc")
    List<Job> findVisibleTo(@Param("uid") UUID uid);
}
