package com.flowdesk.auth.repository;

import com.flowdesk.auth.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t JOIN FETCH t.user WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
            SET t.consumedAt = :now
            WHERE t.user.id = :userId
              AND t.consumedAt IS NULL
            """)
    int invalidateActiveTokensForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            DELETE FROM PasswordResetToken t
            WHERE (t.expiresAt < :cutoff AND t.expiresAt < :now)
               OR (t.consumedAt IS NOT NULL AND t.consumedAt < :cutoff)
            """)
    int deleteTokensOlderThan(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
