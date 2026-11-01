package com.flowdesk.backend;

import com.flowdesk.auth.ratelimit.AcceptInvitationRateLimitingFilter;
import com.flowdesk.auth.ratelimit.LoginRateLimitingFilter;
import com.flowdesk.auth.ratelimit.OAuthRateLimitingFilter;
import com.flowdesk.auth.ratelimit.PasswordResetRateLimitingFilter;
import com.flowdesk.auth.security.FlowDeskUserDetailsService;
import com.flowdesk.auth.security.JwtCurrentUserAuthoritiesConverter;
import com.flowdesk.auth.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("SecurityConfig Conditionality and Web Filter Chain Tests")
class SecurityConfigConditionalityTest {

    private static final String BASE64_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLTEyMzQ1Ng==";

    private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class, MockDependenciesConfig.class)
            .withPropertyValues(
                    "app.security.jwt.secret=" + BASE64_SECRET,
                    "app.security.jwt.issuer=flowdesk",
                    "app.cors.allowed-origin=http://localhost:3000"
            );

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration.class,
                    SecurityFilterAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class
            ))
            .withUserConfiguration(SecurityConfig.class, MockDependenciesConfig.class)
            .withPropertyValues(
                    "app.security.jwt.secret=" + BASE64_SECRET,
                    "app.security.jwt.issuer=flowdesk",
                    "app.cors.allowed-origin=http://localhost:3000"
            );

    @Configuration
    static class MockDependenciesConfig {
        @Bean
        SecurityErrorResponseWriter securityErrorResponseWriter() {
            return mock(SecurityErrorResponseWriter.class);
        }

        @Bean
        FlowDeskUserDetailsService flowDeskUserDetailsService() {
            return mock(FlowDeskUserDetailsService.class);
        }

        @Bean
        JwtCurrentUserAuthoritiesConverter jwtCurrentUserAuthoritiesConverter() {
            return mock(JwtCurrentUserAuthoritiesConverter.class);
        }

        @Bean
        LoginRateLimitingFilter loginRateLimitingFilter() {
            return mock(LoginRateLimitingFilter.class);
        }

        @Bean
        PasswordResetRateLimitingFilter passwordResetRateLimitingFilter() {
            return mock(PasswordResetRateLimitingFilter.class);
        }

        @Bean
        AcceptInvitationRateLimitingFilter acceptInvitationRateLimitingFilter() {
            return mock(AcceptInvitationRateLimitingFilter.class);
        }

        @Bean
        OAuthRateLimitingFilter oauthRateLimitingFilter() {
            return mock(OAuthRateLimitingFilter.class);
        }
    }

    @Test
    @DisplayName("Non-web context (e.g. CLI bootstrap runner) must not register SecurityFilterChain or CorsConfigurationSource")
    void nonWebContextDoesNotLoadServletBeans() {
        nonWebContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            assertThat(context).doesNotHaveBean(CorsConfigurationSource.class);
            // Core non-web security beans remain available
            assertThat(context).hasSingleBean(PasswordEncoder.class);
            assertThat(context).hasSingleBean(JwtDecoder.class);
        });
    }

    @Test
    @DisplayName("Servlet web application context must register SecurityFilterChain and CorsConfigurationSource")
    void webContextLoadsServletSecurityBeans() {
        webContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(context).hasSingleBean(CorsConfigurationSource.class);
            assertThat(context).hasSingleBean(PasswordEncoder.class);
            assertThat(context).hasSingleBean(JwtDecoder.class);
        });
    }
}
