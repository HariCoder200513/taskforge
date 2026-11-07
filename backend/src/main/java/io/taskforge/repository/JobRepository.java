package io.taskforge.repository;

import io.taskforge.domain.Job;
import io.taskforge.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Atomically reserve up to {@code batch} due, pending jobs for this worker.
     * FOR UPDATE SKIP LOCKED is the Postgres queue primitive: concurrent workers
     * each lock a disjoint set of rows and skip rows another worker already holds,
     * so no job is ever claimed twice. The caller must run this inside a
     * transaction and then move the returned rows to RUNNING before committing —
     * the row locks are what keep other workers off them until then.
     */
    @Query(value = """
            select id from jobs
            where status = 'PENDING' and available_at <= now()
            order by available_at
            for update skip locked
            limit :batch
            """, nativeQuery = true)
    List<UUID> lockClaimableIds(@Param("batch") int batch);

    /** RUNNING jobs whose lease has gone stale — their worker likely died. */
    @Query("select j from Job j where j.status = :running and j.heartbeatAt < :cutoff")
    List<Job> findStaleRunning(@Param("running") JobStatus running, @Param("cutoff") Instant cutoff);
}
