package com.flowdesk.user.repository;

import com.flowdesk.user.domain.UserInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {

    Optional<UserInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ui FROM UserInvitation ui WHERE ui.tokenHash = :tokenHash")
    Optional<UserInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ui FROM UserInvitation ui WHERE ui.user.id = :userId AND ui.consumedAt IS NULL")
    Optional<UserInvitation> findActiveByUserIdForUpdate(@Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserInvitation ui SET ui.consumedAt = :now WHERE ui.user.id = :userId AND ui.consumedAt IS NULL")
    int invalidateActiveInvitationsForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserInvitation ui WHERE ui.expiresAt < :cutoff OR (ui.consumedAt IS NOT NULL AND ui.consumedAt < :cutoff)")
    int deleteOldInvitations(@Param("cutoff") Instant cutoff);
}
