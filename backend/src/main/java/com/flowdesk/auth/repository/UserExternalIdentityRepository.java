package com.flowdesk.auth.repository;

import com.flowdesk.auth.domain.UserExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserExternalIdentityRepository extends JpaRepository<UserExternalIdentity, UUID> {

    Optional<UserExternalIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    Optional<UserExternalIdentity> findByUserIdAndProvider(UUID userId, String provider);

    List<UserExternalIdentity> findByUserId(UUID userId);

    boolean existsByProviderAndProviderSubject(String provider, String providerSubject);
}
