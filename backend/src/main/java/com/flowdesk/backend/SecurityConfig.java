package com.flowdesk.backend;

import com.flowdesk.auth.security.FlowDeskUserDetailsService;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

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
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {

        http
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

                        // User registration
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users"
                        )
                        .permitAll()

                        // Login
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        )
                        .permitAll()

                        /*
                         * Day 16 WebSocket handshake.
                         *
                         * The HTTP upgrade itself is public.
                         *
                         * Authentication happens afterwards
                         * inside the STOMP CONNECT frame using
                         * WebSocketJwtChannelInterceptor.
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

                        // Support Engineer queue
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/tickets/queue"
                        )
                        .hasRole(
                                "SUPPORT_ENGINEER"
                        )

                        // Support Engineer claim endpoint
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/tickets/*/claim"
                        )
                        .hasRole(
                                "SUPPORT_ENGINEER"
                        )

                        // Support Engineer workbench
                        .requestMatchers(
                                "/api/support/**"
                        )
                        .hasRole(
                                "SUPPORT_ENGINEER"
                        )

                        // Everything else requires JWT
                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
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
    public AuthenticationProvider
            authenticationProvider(
                    FlowDeskUserDetailsService
                            userDetailsService,

                    PasswordEncoder
                            passwordEncoder
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
    public AuthenticationManager
            authenticationManager(
                    AuthenticationProvider
                            authenticationProvider
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
                Base64
                        .getDecoder()
                        .decode(
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
                .withSecretKey(
                        secretKey
                )
                .algorithm(
                        MacAlgorithm.HS256
                )
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
                        .withSecretKey(
                                secretKey
                        )
                        .macAlgorithm(
                                MacAlgorithm.HS256
                        )
                        .build();

        decoder.setJwtValidator(
                JwtValidators
                        .createDefaultWithIssuer(
                                issuer
                        )
        );

        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter
            jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter
                authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter
                .setAuthoritiesClaimName(
                        "roles"
                );

        authoritiesConverter
                .setAuthorityPrefix(
                        "ROLE_"
                );

        JwtAuthenticationConverter
                authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter
                .setJwtGrantedAuthoritiesConverter(
                        authoritiesConverter
                );

        return authenticationConverter;
    }

    @Bean
    public CorsConfigurationSource
            corsConfigurationSource(
                    @Value("${app.cors.allowed-origin}")
                    String allowedOrigin
            ) {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(
                        allowedOrigin
                )
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

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}