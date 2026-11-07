package io.taskforge.domain;

/** Permissions a user can hold on a queue (see queue_permissions table). */
public enum QueuePermissionType {
    VIEW,
    SUBMIT,
    MANAGE,
    RETRY
}
