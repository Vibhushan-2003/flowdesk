package com.flowdesk.user.service;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.dto.CreateUserRequest;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.flowdesk.user.exception.DuplicateEmailException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        Role employeeRole = roleRepository
                .findByCode(RoleCode.EMPLOYEE)
                .orElseThrow(() ->
                        new IllegalStateException("EMPLOYEE role is not configured")
                );

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                normalizedEmail,
                passwordHash,
                request.firstName().trim(),
                request.lastName().trim()
        );

        user.assignRole(employeeRole);

        User savedUser = userRepository.saveAndFlush(user);

        return toResponse(savedUser);
    }

    private UserResponse toResponse(User user) {

        Set<RoleCode> roles = user.getRoles()
                .stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                roles,
                user.getCreatedAt()
        );
    }
}