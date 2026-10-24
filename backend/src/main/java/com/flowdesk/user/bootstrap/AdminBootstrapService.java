package com.flowdesk.user.bootstrap;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminBootstrapService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Executes the bootstrap provisioning inside an isolated transaction.
     *
     * Concurrency & Safety Guarantee:
     * 1. Acquires a PESSIMISTIC_WRITE lock on the ADMIN role row (seeded by Flyway V1).
     *    In PostgreSQL, this serializes concurrent bootstrap attempts at the row level.
     * 2. Verifies that the total count of users in the system is exactly 0.
     *    If ANY user exists (active, inactive, admin or regular employee), the operation aborts.
     * 3. Creates exactly one ACTIVE user with role ADMIN.
     * 4. Hashes the password using the configured PasswordEncoder.
     * 5. Emits a SYSTEM USER_CREATED audit event with safe metadata.
     * 6. Flushes to database. If any step fails (including audit), the entire transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public UserResponse provisionFirstAdmin(AdminBootstrapProperties properties) {
        properties.validate();

        log.info("Acquiring lock on ADMIN role row for initial bootstrap check...");
        Role adminRole = roleRepository.findByCodeForUpdate(RoleCode.ADMIN)
                .orElseThrow(() -> new AdminBootstrapException("ADMIN role is not seeded in database. Cannot bootstrap."));

        long totalUserCount = userRepository.count();
        if (totalUserCount > 0) {
            throw new AdminBootstrapException(
                    "Database is not in initial state: " + totalUserCount + " user(s) already exist. Bootstrap refused."
            );
        }

        String normalizedEmail = properties.getEmail().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(properties.getPassword());

        User adminUser = new User(
                normalizedEmail,
                passwordHash,
                properties.getFirstName().trim(),
                properties.getLastName().trim()
        );

        adminUser.assignRole(adminRole);
        User savedAdmin = userRepository.saveAndFlush(adminUser);

        auditService.recordSystemAction(
                AuditAction.USER_CREATED,
                AuditTargetType.USER,
                savedAdmin.getId(),
                savedAdmin.getEmail(),
                Map.of(
                        "source", "INITIAL_ADMIN_BOOTSTRAP",
                        "assignedRoles", List.of(RoleCode.ADMIN.name())
                )
        );

        log.info("Successfully provisioned initial administrator with email: {}", savedAdmin.getEmail());

        Set<RoleCode> roleCodes = savedAdmin.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        return new UserResponse(
                savedAdmin.getId(),
                savedAdmin.getEmail(),
                savedAdmin.getFirstName(),
                savedAdmin.getLastName(),
                savedAdmin.getStatus(),
                roleCodes,
                savedAdmin.getCreatedAt()
        );
    }
}
