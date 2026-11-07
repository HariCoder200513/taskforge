package io.taskforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} support (the worker poll/reap loops). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
