package io.taskforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "queue_permissions")
public class QueuePermission {

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "queue_id")
        private UUID queueId;
        @Column(name = "user_id")
        private UUID userId;
        @Column(name = "permission")
        private String permission;

        protected Id() {
        }

        public Id(UUID queueId, UUID userId, String permission) {
            this.queueId = queueId;
            this.userId = userId;
            this.permission = permission;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(queueId, other.queueId)
                    && Objects.equals(userId, other.userId)
                    && Objects.equals(permission, other.permission);
        }

        @Override
        public int hashCode() {
            return Objects.hash(queueId, userId, permission);
        }
    }

    @EmbeddedId
    private Id id;

    protected QueuePermission() {
    }

    public QueuePermission(UUID queueId, UUID userId, QueuePermissionType permission) {
        this.id = new Id(queueId, userId, permission.name());
    }

    public Id getId() {
        return id;
    }
}
