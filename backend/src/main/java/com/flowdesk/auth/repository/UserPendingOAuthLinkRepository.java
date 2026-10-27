package com.flowdesk.auth.repository;

import com.flowdesk.auth.domain.UserPendingOAuthLink;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPendingOAuthLinkRepository extends JpaRepository<UserPendingOAuthLink, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UserPendingOAuthLink p WHERE p.tokenHash = :tokenHash")
    Optional<UserPendingOAuthLink> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<UserPendingOAuthLink> findByTokenHash(String tokenHash);
}
