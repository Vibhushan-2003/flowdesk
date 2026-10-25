
package com.flowdesk.auth.security;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtCurrentUserAuthoritiesConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final UserRepository userRepository;

    public JwtCurrentUserAuthoritiesConverter(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        UUID userId;

        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw invalidAccount();
        }

        User user = userRepository
                .findWithRolesById(userId)
                .orElseThrow(this::invalidAccount);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidAccount();
        }

        Object versionClaim = jwt.getClaim("credential_version");
        if (versionClaim == null) {
            throw invalidAccount();
        }

        long tokenVersion;
        if (versionClaim instanceof Number number) {
            tokenVersion = number.longValue();
        } else {
            try {
                tokenVersion = Long.parseLong(versionClaim.toString().trim());
            } catch (NumberFormatException exception) {
                throw invalidAccount();
            }
        }

        if (tokenVersion != user.getCredentialVersion()) {
            throw invalidAccount();
        }

        Set<GrantedAuthority> authorities =
                user.getRoles()
                        .stream()
                        .map(Role::getCode)
                        .map(role ->
                                (GrantedAuthority)
                                        new SimpleGrantedAuthority(
                                                "ROLE_" + role.name()
                                        )
                        )
                        .collect(Collectors.toUnmodifiableSet());

        if (authorities.isEmpty()) {
            throw invalidAccount();
        }

        return authorities;
    }

    private OAuth2AuthenticationException invalidAccount() {
        return new OAuth2AuthenticationException(
                new OAuth2Error(
                        "invalid_token",
                        "Account is unavailable",
                        null
                )
        );
    }
}
