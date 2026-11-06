package io.taskforge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Role rows are seeded by Flyway; this entity is read-only in the app. */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    private Short id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    protected Role() {
    }

    public Short getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
