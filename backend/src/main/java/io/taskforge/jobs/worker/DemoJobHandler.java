package io.taskforge.jobs.worker;

import io.taskforge.domain.Job;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Default handler until real executors exist. It gives the engine deterministic,
 * payload-driven behavior to exercise the full lifecycle:
 *
 *   {"fail": true}            → throws (drives retry / dead-letter)
 *   {"fail": true, "failMessage": "..."} → throws with that message
 *   {"sleepMs": 50}           → sleeps, then succeeds (exercises leases)
 *   anything else             → succeeds, echoing the payload
 */
@Component
public class DemoJobHandler implements JobHandler {

    @Override
    public Map<String, Object> handle(Job job) throws Exception {
        Map<String, Object> payload = job.getPayload() != null ? job.getPayload() : Map.of();

        if (Boolean.TRUE.equals(payload.get("fail"))) {
            Object msg = payload.getOrDefault("failMessage", "simulated failure");
            throw new RuntimeException(String.valueOf(msg));
        }

        Object sleepMs = payload.get("sleepMs");
        if (sleepMs instanceof Number n) {
            Thread.sleep(Math.max(0, n.longValue()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("handled", true);
        result.put("attempt", job.getAttempts());
        result.put("echo", payload);
        return result;
    }
}
