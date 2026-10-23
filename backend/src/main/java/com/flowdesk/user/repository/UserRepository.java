package com.flowdesk.user.repository;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository
        extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "roles")
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findWithRolesById(
            @Param("userId") UUID userId
    );

    Optional<User> findByEmailIgnoreCase(
            String email
    );

    boolean existsByEmailIgnoreCase(
            String email
    );

    List<User> findDistinctByRoles_CodeInAndStatus(
            Collection<RoleCode> roleCodes,
            UserStatus status
    );
}
