package io.taskforge.jobs;

import io.taskforge.jobs.dto.JobQueueDtos.JobDto;
import io.taskforge.jobs.dto.JobQueueDtos.SubmitJobRequest;
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
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobQueueService service;

    public JobController(JobQueueService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public ResponseEntity<JobDto> submit(@Valid @RequestBody SubmitJobRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JobDto.from(service.submitJob(UUID.fromString(req.queueId()), req.payload())));
    }

    @GetMapping
    public List<JobDto> list() {
        return service.listJobs().stream().map(JobDto::from).toList();
    }

    @GetMapping("/{id}")
    public JobDto get(@PathVariable UUID id) {
        return JobDto.from(service.getJob(id));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','DEVELOPER')")
    public JobDto retry(@PathVariable UUID id) {
        return JobDto.from(service.retryJob(id));
    }
}
