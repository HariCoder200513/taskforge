package io.taskforge.repository;

import io.taskforge.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // email column is citext, so lookups are case-insensitive at the DB level.
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** How many users currently hold the given role — used for the last-admin guard. */
    @Query("select count(u) from User u join u.roles r where r.name = :role")
    long countByRoleName(@Param("role") String role);
}
