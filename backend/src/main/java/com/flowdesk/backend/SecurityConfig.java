
package com.flowdesk.backend;

import com.flowdesk.auth.security.FlowDeskUserDetailsService;
import com.flowdesk.auth.security.JwtCurrentUserAuthoritiesConverter;
import com.flowdesk.auth.security.SecurityErrorResponseWriter;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.flowdesk.auth.ratelimit.LoginRateLimitingFilter;
import com.flowdesk.auth.ratelimit.PasswordResetRateLimitingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource,
            SecurityErrorResponseWriter securityErrorResponseWriter,
            LoginRateLimitingFilter loginRateLimitingFilter,
            PasswordResetRateLimitingFilter passwordResetRateLimitingFilter
    ) throws Exception {

        // Day 43: Preserve the standard Bearer authentication challenge.
        BearerTokenAuthenticationEntryPoint bearerEntryPoint =
                new BearerTokenAuthenticationEntryPoint();

        http
                .addFilterBefore(
                        loginRateLimitingFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        passwordResetRateLimitingFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource
                        )
                )

                .csrf(csrf ->
                        csrf.disable()
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Public health endpoint
                        .requestMatchers(
                                "/api/health"
                        )
                        .permitAll()

                        /*
                         * Day 35: Secure user provisioning.
                         *
                         * Only administrators can create users.
                         * New users receive EMPLOYEE role
                         * through UserService.
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users"
                        )
                        .hasRole("ADMIN")

                        // Public login
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        )
                        .permitAll()

                        // Public password recovery
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        )
                        .permitAll()

                        /*
                         * WebSocket handshake is public.
                         * Authentication is handled during
                         * STOMP CONNECT.
                         */
                        .requestMatchers(
                                "/ws",
                                "/ws/**"
                        )
                        .permitAll()

                        .requestMatchers(
                                "/error"
                        )
                        .permitAll()

                        // Audit trail - ADMIN only
                        .requestMatchers(
                                "/api/audit/**"
                        )
                        .hasRole("ADMIN")

                        // User management - ADMIN only
                        .requestMatchers(
                                "/api/admin/**"
                        )
                        .hasRole("ADMIN")

                        // SLA dashboard
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/sla/dashboard"
                        )
                        .hasAnyRole(
                                "TEAM_LEAD",
                                "ADMIN"
                        )

                        // Support engineer queue
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/tickets/queue"
                        )
                        .hasRole("SUPPORT_ENGINEER")

                        // Support engineer ticket claiming
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/tickets/*/claim"
                        )
                        .hasRole("SUPPORT_ENGINEER")

                        // Support engineer workbench
                        .requestMatchers(
                                "/api/support/**"
                        )
                        .hasRole("SUPPORT_ENGINEER")

                        // Approval workflow
                        .requestMatchers(
                                "/api/approvals/**"
                        )
                        .hasAnyRole(
                                "TEAM_LEAD",
                                "ADMIN"
                        )

                        // All remaining endpoints require JWT
                        .anyRequest()
                        .authenticated()
                )

                /*
                 * Day 43: Standard JSON responses for
                 * authentication and authorization errors.
                 *
                 * The Bearer entry point writes the required
                 * WWW-Authenticate header.
                 *
                 * Our response writer then writes the
                 * standardized ApiError JSON body.
                 */
                .exceptionHandling(exceptions -> exceptions

                        .authenticationEntryPoint(
                                (request, response, exception) -> {

                                    bearerEntryPoint.commence(
                                            request,
                                            response,
                                            exception
                                    );

                                    securityErrorResponseWriter.write(
                                            request,
                                            response,
                                            HttpStatus.UNAUTHORIZED,
                                            "Authentication required"
                                    );
                                }
                        )

                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        securityErrorResponseWriter.write(
                                                request,
                                                response,
                                                HttpStatus.FORBIDDEN,
                                                "Access denied"
                                        )
                        )
                )

                /*
                 * Day 43: JWT resource-server errors.
                 *
                 * Missing/invalid authentication returns 401.
                 * Insufficient permissions return 403.
                 *
                 * Existing JWT conversion remains unchanged.
                 */
                .oauth2ResourceServer(oauth2 -> oauth2

                        .authenticationEntryPoint(
                                (request, response, exception) -> {

                                    bearerEntryPoint.commence(
                                            request,
                                            response,
                                            exception
                                    );

                                    securityErrorResponseWriter.write(
                                            request,
                                            response,
                                            HttpStatus.UNAUTHORIZED,
                                            "Authentication required"
                                    );
                                }
                        )

                        .accessDeniedHandler(
                                (request, response, exception) ->
                                        securityErrorResponseWriter.write(
                                                request,
                                                response,
                                                HttpStatus.FORBIDDEN,
                                                "Access denied"
                                        )
                        )

                        .jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            FlowDeskUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(
                        userDetailsService
                );

        provider.setPasswordEncoder(
                passwordEncoder
        );

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationProvider authenticationProvider
    ) {

        return new ProviderManager(
                authenticationProvider
        );
    }

    @Bean
    public SecretKey jwtSecretKey(
            @Value("${app.security.jwt.secret}")
            String encodedSecret
    ) {

        byte[] keyBytes =
                Base64.getDecoder().decode(
                        encodedSecret
                );

        return new SecretKeySpec(
                keyBytes,
                "HmacSHA256"
        );
    }

    @Bean
    public JwtEncoder jwtEncoder(
            SecretKey secretKey
    ) {

        return NimbusJwtEncoder
                .withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey secretKey,
            @Value("${app.security.jwt.issuer}")
            String issuer
    ) {

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withSecretKey(secretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(
                        issuer
                )
        );

        return decoder;
    }

    /*
     * Day 36: Current database-backed roles.
     *
     * JWT signature and expiration are validated
     * by JwtDecoder.
     *
     * After that, currentUserAuthorities checks:
     *
     * 1. Whether the user exists.
     * 2. Whether the account is ACTIVE.
     * 3. Which roles the user currently has.
     *
     * Old JWT roles are no longer trusted
     * for authorization decisions.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            JwtCurrentUserAuthoritiesConverter currentUserAuthorities
    ) {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                currentUserAuthorities
        );

        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}")
            String allowedOrigin
    ) {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(allowedOrigin)
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );

        configuration.setAllowCredentials(
                false
        );

        configuration.setExposedHeaders(
                List.of("X-Request-ID")
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}
