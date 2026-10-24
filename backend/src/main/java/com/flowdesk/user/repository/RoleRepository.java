package com.flowdesk.user.repository;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(RoleCode code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.code = :code")
    Optional<Role> findByCodeForUpdate(@Param("code") RoleCode code);

    List<Role> findByCodeIn(Collection<RoleCode> codes);
}