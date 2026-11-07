package io.taskforge.jobs;

import io.taskforge.domain.QueuePermissionType;
import io.taskforge.jobs.dto.JobQueueDtos.CreateQueueRequest;
import io.taskforge.jobs.dto.JobQueueDtos.GrantPermissionRequest;
import io.taskforge.jobs.dto.JobQueueDtos.QueueDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/queues")
public class QueueController {

    private final JobQueueService service;

    public QueueController(JobQueueService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QueueDto> create(@Valid @RequestBody CreateQueueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QueueDto.from(service.createQueue(req.name())));
    }

    @GetMapping
    public List<QueueDto> list() {
        return service.listQueues().stream().map(QueueDto::from).toList();
    }

    @GetMapping("/{id}")
    public QueueDto get(@PathVariable UUID id) {
        return QueueDto.from(service.getQueue(id));
    }

    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> grant(@PathVariable UUID id,
                                      @Valid @RequestBody GrantPermissionRequest req) {
        service.grantPermission(id, UUID.fromString(req.userId()),
                QueuePermissionType.valueOf(req.permission().toUpperCase()));
        return ResponseEntity.noContent().build();
    }
}
