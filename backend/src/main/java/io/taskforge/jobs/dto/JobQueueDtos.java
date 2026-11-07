package io.taskforge.jobs.dto;

import io.taskforge.domain.Job;
import io.taskforge.domain.Queue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class JobQueueDtos {

    private JobQueueDtos() {
    }

    public record CreateQueueRequest(
            @NotBlank @Size(max = 200) String name
    ) {}

    public record QueueDto(String id, String name, String ownerId, Instant createdAt) {
        public static QueueDto from(Queue q) {
            return new QueueDto(q.getId().toString(), q.getName(), q.getOwnerId().toString(), q.getCreatedAt());
        }
    }

    public record GrantPermissionRequest(
            @NotBlank String userId,
            @NotBlank String permission   // VIEW | SUBMIT | MANAGE | RETRY
    ) {}

    public record SubmitJobRequest(
            @NotBlank String queueId,
            @NotNull Map<String, Object> payload
    ) {}

    public record JobDto(String id, String queueId, String ownerId, String status, Instant createdAt) {
        public static JobDto from(Job j) {
            return new JobDto(j.getId().toString(), j.getQueueId().toString(),
                    j.getOwnerId().toString(), j.getStatus().name(), j.getCreatedAt());
        }
    }
}
