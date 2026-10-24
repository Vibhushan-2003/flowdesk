package com.flowdesk.user.repository;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Query("""
            SELECT COUNT(u)
            FROM User u
            JOIN u.roles r
            WHERE r.code = :code
              AND u.status = :status
            """)
    long countByRolesCodeAndStatus(
            @Param("code") RoleCode code,
            @Param("status") UserStatus status
    );

    @Query(
            value = """
                    SELECT u
                    FROM User u
                    WHERE (:status IS NULL OR u.status = :status)
                      AND (:role IS NULL OR EXISTS (
                          SELECT 1 FROM u.roles r WHERE r.code = :role
                      ))
                      AND (
                          :searchPattern IS NULL
                          OR LOWER(u.email) LIKE :searchPattern ESCAPE '!'
                          OR LOWER(u.firstName) LIKE :searchPattern ESCAPE '!'
                          OR LOWER(u.lastName) LIKE :searchPattern ESCAPE '!'
                          OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :searchPattern ESCAPE '!'
                      )
                    ORDER BY u.createdAt DESC, u.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(u)
                    FROM User u
                    WHERE (:status IS NULL OR u.status = :status)
                      AND (:role IS NULL OR EXISTS (
                          SELECT 1 FROM u.roles r WHERE r.code = :role
                      ))
                      AND (
                          :searchPattern IS NULL
                          OR LOWER(u.email) LIKE :searchPattern ESCAPE '!'
                          OR LOWER(u.firstName) LIKE :searchPattern ESCAPE '!'
                          OR LOWER(u.lastName) LIKE :searchPattern ESCAPE '!'
                          OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :searchPattern ESCAPE '!'
                      )
                    """
    )
    Page<User> searchUsers(
            @Param("searchPattern") String searchPattern,
            @Param("role") RoleCode role,
            @Param("status") UserStatus status,
            Pageable pageable
    );
}
