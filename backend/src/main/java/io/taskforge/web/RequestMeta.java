package io.taskforge.web;

/** Per-request metadata used for auditing and token binding. */
public record RequestMeta(String ip, String userAgent, String requestId) {
}
