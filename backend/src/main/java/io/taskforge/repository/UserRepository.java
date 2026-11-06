package io.taskforge.repository;

import io.taskforge.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // email column is citext, so lookups are case-insensitive at the DB level.
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
