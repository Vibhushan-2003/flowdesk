package com.flowdesk.user.controller;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.dto.TestFixtureCreateUserRequest;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Internal test-fixture controller for automated E2E integration test suites.
 * Strictly disabled in production and demo environments via ConditionalOnProperty.
 * Only permits provisioning accounts with the @flowdesk.test reserved domain.
 */
@RestController
@RequestMapping("/api/internal/test-fixtures")
@ConditionalOnProperty(name = "flowdesk.test.fixtures.enabled", havingValue = "true")
public class TestFixtureController {

    private static final Logger log = LoggerFactory.getLogger(TestFixtureController.class);
    private static final String ALLOWED_TEST_DOMAIN = "@flowdesk.test";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public TestFixtureController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.roleRepository = Objects.requireNonNull(roleRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<UserResponse> provisionTestUser(
            @Valid @RequestBody TestFixtureCreateUserRequest request
    ) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.endsWith(ALLOWED_TEST_DOMAIN)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Test fixtures may only provision test accounts ending with " + ALLOWED_TEST_DOMAIN
            );
        }

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User account already exists: " + normalizedEmail
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(
                normalizedEmail,
                passwordHash,
                request.firstName().trim(),
                request.lastName().trim()
        );

        for (RoleCode roleCode : request.roles()) {
            Role role = roleRepository.findByCode(roleCode)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Role not found: " + roleCode
                    ));
            user.assignRole(role);
        }

        User savedUser = userRepository.save(user);
        log.info("Internal test-fixture provisioned test account [{}] with roles {}",
                savedUser.getEmail(), request.roles());

        Set<RoleCode> assignedRoles = savedUser.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFirstName(),
                savedUser.getLastName(),
                savedUser.getStatus(),
                assignedRoles,
                savedUser.getCreatedAt()
        ));
    }
}
