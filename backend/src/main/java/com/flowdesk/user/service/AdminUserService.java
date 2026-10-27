package com.flowdesk.user.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.dto.AdminCreateUserRequest;
import com.flowdesk.user.dto.UpdateUserRolesRequest;
import com.flowdesk.user.dto.UpdateUserStatusRequest;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.exception.DuplicateEmailException;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminUserService(
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

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(
            int page,
            int size,
            String search,
            RoleCode role,
            UserStatus status
    ) {
        validatePagination(page, size);

        String searchPattern = buildSearchPattern(search);
        Pageable pageable = PageRequest.of(page, size);

        Page<User> userPage = userRepository.searchUsers(
                searchPattern,
                role,
                status,
                pageable
        );

        List<UserResponse> items = userPage.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(
                items,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isFirst(),
                userPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        return userRepository.findWithRolesById(userId)
                .map(this::toResponse)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );
    }

    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request, UUID actorUserId) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateEmailException(normalizedEmail);
        }

        User actorUser = userRepository.findWithRolesById(actorUserId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Authenticated user not found"
                        )
                );

        List<Role> resolvedRoles = roleRepository.findByCodeIn(request.roles());
        if (resolvedRoles.size() != request.roles().size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or more specified roles are invalid"
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                normalizedEmail,
                passwordHash,
                request.firstName().trim(),
                request.lastName().trim()
        );

        user.replaceRoles(new HashSet<>(resolvedRoles));
        User savedUser = userRepository.saveAndFlush(user);

        List<String> assignedRoleNames = request.roles()
                .stream()
                .map(RoleCode::name)
                .sorted()
                .toList();

        auditService.recordUserAction(
                actorUser,
                AuditAction.USER_CREATED,
                AuditTargetType.USER,
                savedUser.getId(),
                savedUser.getEmail(),
                Map.of("assignedRoles", assignedRoleNames)
        );

        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUserRoles(
            UUID targetUserId,
            UpdateUserRolesRequest request,
            UUID actorUserId
    ) {
        User targetUser = userRepository.findWithRolesById(targetUserId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );

        User actorUser = userRepository.findWithRolesById(actorUserId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Authenticated user not found"
                        )
                );

        Set<RoleCode> currentRoleCodes = targetUser.getRoles()
                .stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        Set<RoleCode> newRoleCodes = request.roles();

        if (currentRoleCodes.equals(newRoleCodes)) {
            return toResponse(targetUser);
        }

        // Self-demotion protection
        if (actorUserId.equals(targetUserId) && !newRoleCodes.contains(RoleCode.ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Administrators cannot remove their own ADMIN role"
            );
        }

        // Last active admin protection with pessimistic locking
        if (currentRoleCodes.contains(RoleCode.ADMIN) && !newRoleCodes.contains(RoleCode.ADMIN)) {
            roleRepository.findByCodeForUpdate(RoleCode.ADMIN);

            if (targetUser.getStatus() == UserStatus.ACTIVE) {
                long activeAdminCount = userRepository.countByRolesCodeAndStatus(
                        RoleCode.ADMIN,
                        UserStatus.ACTIVE
                );
                if (activeAdminCount <= 1) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Cannot remove the ADMIN role from the last active administrator"
                    );
                }
            }
        }

        List<Role> resolvedRoles = roleRepository.findByCodeIn(newRoleCodes);
        if (resolvedRoles.size() != newRoleCodes.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "One or more specified roles are invalid"
            );
        }

        targetUser.replaceRoles(new HashSet<>(resolvedRoles));
        User savedUser = userRepository.saveAndFlush(targetUser);

        auditService.recordUserAction(
                actorUser,
                AuditAction.USER_ROLES_UPDATED,
                AuditTargetType.USER,
                savedUser.getId(),
                savedUser.getEmail(),
                Map.of(
                        "previousRoles", currentRoleCodes.stream().map(RoleCode::name).sorted().toList(),
                        "newRoles", newRoleCodes.stream().map(RoleCode::name).sorted().toList()
                )
        );

        return toResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUserStatus(
            UUID targetUserId,
            UpdateUserStatusRequest request,
            UUID actorUserId
    ) {
        User targetUser = userRepository.findWithRolesById(targetUserId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );

        User actorUser = userRepository.findWithRolesById(actorUserId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Authenticated user not found"
                        )
                );

        UserStatus currentStatus = targetUser.getStatus();
        UserStatus newStatus = request.status();

        if (currentStatus == UserStatus.INVITED || newStatus == UserStatus.INVITED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot manually change status of an invited user or transition user to invited"
            );
        }

        if (currentStatus == newStatus) {
            return toResponse(targetUser);
        }

        if (newStatus == UserStatus.INACTIVE) {
            // Self-deactivation protection
            if (actorUserId.equals(targetUserId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Administrators cannot deactivate their own account"
                );
            }

            // Last active admin protection with pessimistic locking
            boolean isTargetAdmin = targetUser.getRoles()
                    .stream()
                    .anyMatch(role -> role.getCode() == RoleCode.ADMIN);

            if (isTargetAdmin) {
                roleRepository.findByCodeForUpdate(RoleCode.ADMIN);

                long activeAdminCount = userRepository.countByRolesCodeAndStatus(
                        RoleCode.ADMIN,
                        UserStatus.ACTIVE
                );
                if (activeAdminCount <= 1) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Cannot deactivate the last active administrator"
                    );
                }
            }

            targetUser.deactivate();
            User savedUser = userRepository.saveAndFlush(targetUser);

            auditService.recordUserAction(
                    actorUser,
                    AuditAction.USER_DEACTIVATED,
                    AuditTargetType.USER,
                    savedUser.getId(),
                    savedUser.getEmail(),
                    Map.of(
                            "previousStatus", "ACTIVE",
                            "newStatus", "INACTIVE"
                    )
            );

            return toResponse(savedUser);
        } else {
            targetUser.activate();
            User savedUser = userRepository.saveAndFlush(targetUser);

            auditService.recordUserAction(
                    actorUser,
                    AuditAction.USER_ACTIVATED,
                    AuditTargetType.USER,
                    savedUser.getId(),
                    savedUser.getEmail(),
                    Map.of(
                            "previousStatus", "INACTIVE",
                            "newStatus", "ACTIVE"
                    )
            );

            return toResponse(savedUser);
        }
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

    private String buildSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String normalized = search.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search query must be 100 characters or fewer"
            );
        }

        String escaped = normalized
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escaped + "%";
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }
    }
}
