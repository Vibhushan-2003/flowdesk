
package com.flowdesk.auth.security;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtCurrentUserAuthoritiesConverterTest {

    private UserRepository repository;
    private JwtCurrentUserAuthoritiesConverter converter;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        converter = new JwtCurrentUserAuthoritiesConverter(repository);
    }

    @Test
    void removedAdminRoleMustNotRemainAuthorized() {
        UUID userId = UUID.randomUUID();

        User user = mock(User.class);
        Role employeeRole = mock(Role.class);

        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getRoles()).thenReturn(Set.of(employeeRole));
        when(employeeRole.getCode()).thenReturn(RoleCode.EMPLOYEE);
        when(repository.findWithRolesById(userId))
                .thenReturn(Optional.of(user));

        Collection<GrantedAuthority> authorities =
                converter.convert(jwt(userId, "ADMIN"));

        assertTrue(
                authorities.stream().anyMatch(
                        a -> a.getAuthority().equals("ROLE_EMPLOYEE")
                )
        );

        assertFalse(
                authorities.stream().anyMatch(
                        a -> a.getAuthority().equals("ROLE_ADMIN")
                )
        );
    }

    @Test
    void disabledUserMustBeRejected() {
        UUID userId = UUID.randomUUID();

        User user = mock(User.class);

        when(user.getStatus()).thenReturn(UserStatus.INACTIVE);
        when(repository.findWithRolesById(userId))
                .thenReturn(Optional.of(user));

        assertThrows(
                OAuth2AuthenticationException.class,
                () -> converter.convert(jwt(userId, "EMPLOYEE"))
        );
    }

    @Test
    void deletedUserMustBeRejected() {
        UUID userId = UUID.randomUUID();

        when(repository.findWithRolesById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                OAuth2AuthenticationException.class,
                () -> converter.convert(jwt(userId, "EMPLOYEE"))
        );
    }

    @Test
    void malformedSubjectMustBeRejected() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("not-a-uuid")
                .claim("roles", Set.of("ADMIN"))
                .build();

        assertThrows(
                OAuth2AuthenticationException.class,
                () -> converter.convert(jwt)
        );

        verifyNoInteractions(repository);
    }

    private Jwt jwt(UUID userId, String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claims(claims ->
                        claims.put("roles", Set.of(role))
                )
                .build();
    }
}
