package com.flowdesk.user.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserInvitationPropertiesTest {

    @Test
    @DisplayName("Default configuration should be valid for local development")
    void defaultConfigurationShouldBeValid() {
        UserInvitationProperties properties = new UserInvitationProperties();

        assertThat(properties.getFrontendOrigin()).isEqualTo("http://localhost:5173");
        assertThat(properties.getTokenValidityHours()).isEqualTo(48);
        assertThat(properties.getTokenEntropyBytes()).isEqualTo(32);
        assertThat(properties.isProduction()).isFalse();

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Trailing slashes in frontend-origin should be normalized without double slashes")
    void trailingSlashesShouldBeNormalized() {
        UserInvitationProperties properties = new UserInvitationProperties();

        properties.setFrontendOrigin("http://localhost:5173/");
        assertThat(properties.getFrontendOrigin()).isEqualTo("http://localhost:5173");

        properties.setFrontendOrigin("https://flowdesk.example.com///");
        assertThat(properties.getFrontendOrigin()).isEqualTo("https://flowdesk.example.com");

        properties.setFrontendOrigin("https://flowdesk.example.com");
        assertThat(properties.getFrontendOrigin()).isEqualTo("https://flowdesk.example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Blank frontend-origin should fail validation with IllegalStateException")
    void blankFrontendOriginShouldFail(String blankOrigin) {
        UserInvitationProperties properties = new UserInvitationProperties();
        properties.setFrontendOrigin(blankOrigin);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.invitation.frontend-origin must not be blank");
    }

    @Test
    @DisplayName("Null frontend-origin should fail validation with IllegalStateException")
    void nullFrontendOriginShouldFail() {
        UserInvitationProperties properties = new UserInvitationProperties();
        properties.setFrontendOrigin(null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.invitation.frontend-origin must not be blank");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 721, 1000})
    @DisplayName("Invalid token validity hours should fail validation")
    void invalidTokenValidityHoursShouldFail(int hours) {
        UserInvitationProperties properties = new UserInvitationProperties();
        properties.setTokenValidityHours(hours);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.invitation.token-validity-hours must be between 1 and 720");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 16, 31})
    @DisplayName("Token entropy bytes less than 32 should fail validation")
    void insufficientEntropyShouldFail(int bytes) {
        UserInvitationProperties properties = new UserInvitationProperties();
        properties.setTokenEntropyBytes(bytes);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.security.invitation.token-entropy-bytes must be >= 32 (256 bits)");
    }

    @Test
    @DisplayName("Non-HTTPS frontend origin should fail validation in production mode")
    void nonHttpsInProductionShouldFail() {
        UserInvitationProperties properties = new UserInvitationProperties();
        properties.setProduction(true);
        properties.setFrontendOrigin("http://flowdesk.example.com");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production invitation requires an HTTPS frontend origin");
    }

    @Test
    @DisplayName("HTTPS frontend origin should pass validation in production mode")
    void httpsInProductionShouldPass() {
        UserInvitationProperties properties = new UserInvitationProperties();
        properties.setProduction(true);
        properties.setFrontendOrigin("https://flowdesk.example.com");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Active Spring profile 'prod' should be detected as production")
    void prodProfileShouldBeDetectedAsProduction() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        UserInvitationProperties properties = new UserInvitationProperties();
        properties.setEnvironment(env);
        properties.setFrontendOrigin("http://insecure.flowdesk.com");

        assertThat(properties.isProduction()).isTrue();
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production invitation requires an HTTPS frontend origin");
    }
}
