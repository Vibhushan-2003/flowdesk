package com.flowdesk.email;

import com.flowdesk.email.config.EmailProperties;
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

class EmailPropertiesTest {

    @Test
    @DisplayName("Default configuration should be valid for local development and Mailpit")
    void defaultConfigurationShouldBeValid() {
        EmailProperties properties = new EmailProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getHost()).isEqualTo("localhost");
        assertThat(properties.getPort()).isEqualTo(1025);
        assertThat(properties.isAuth()).isFalse();
        assertThat(properties.isStarttls()).isFalse();
        assertThat(properties.isSsl()).isFalse();
        assertThat(properties.isProduction()).isFalse();
        assertThat(properties.getFromAddress()).isEqualTo("noreply@flowdesk.internal");
        assertThat(properties.getFromName()).isEqualTo("FlowDesk");
        assertThat(properties.getConnectionTimeoutMs()).isEqualTo(5000);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should fail validation when production mode uses localhost")
    void shouldFailWhenProductionUsesLocalhost() {
        EmailProperties properties = new EmailProperties();
        properties.setProduction(true);
        properties.setHost("localhost");
        properties.setStarttls(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production email configuration requires a non-localhost SMTP host");
    }

    @Test
    @DisplayName("Should fail validation when production mode uses 127.0.0.1")
    void shouldFailWhenProductionUsesLoopback() {
        EmailProperties properties = new EmailProperties();
        properties.setProduction(true);
        properties.setHost("127.0.0.1");
        properties.setStarttls(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production email configuration requires a non-localhost SMTP host");
    }

    @Test
    @DisplayName("Should fail validation when production mode disables both STARTTLS and SSL (fail closed)")
    void shouldFailWhenProductionDisablesEncryption() {
        EmailProperties properties = new EmailProperties();
        properties.setProduction(true);
        properties.setHost("smtp.sendgrid.net");
        properties.setPort(587);
        properties.setAuth(true);
        properties.setUsername("apikey");
        properties.setPassword("SG.test-key");
        properties.setStarttls(false);
        properties.setSsl(false);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production email configuration requires STARTTLS or SSL/TLS encryption. Unencrypted SMTP delivery is forbidden in production.");
    }

    @Test
    @DisplayName("Should automatically enforce production safeguards when prod profile is active")
    void shouldEnforceProductionUnderProdProfileWithoutExplicitFlag() {
        EmailProperties properties = new EmailProperties();
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        properties.setEnvironment(env);

        // Even without setProduction(true), isProduction() must be true
        assertThat(properties.isProduction()).isTrue();

        // With default localhost configuration, validate() must fail closed
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production email configuration requires a non-localhost SMTP host");
    }

    @Test
    @DisplayName("Should automatically enforce production safeguards when 'production' profile is active")
    void shouldEnforceProductionUnderProductionProfileWithoutExplicitFlag() {
        EmailProperties properties = new EmailProperties();
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"production"});
        properties.setEnvironment(env);

        assertThat(properties.isProduction()).isTrue();
    }

    @Test
    @DisplayName("Should fail validation when production mode enables auth without credentials")
    void shouldFailWhenProductionAuthLacksCredentials() {
        EmailProperties properties = new EmailProperties();
        properties.setProduction(true);
        properties.setHost("email-smtp.us-east-1.amazonaws.com");
        properties.setPort(587);
        properties.setStarttls(true);
        properties.setAuth(true);
        properties.setUsername("");
        properties.setPassword("");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("credentials are missing");
    }

    @Test
    @DisplayName("Should pass validation with valid production STARTTLS configuration")
    void shouldPassValidProductionStarttlsConfiguration() {
        EmailProperties properties = new EmailProperties();
        properties.setProduction(true);
        properties.setHost("smtp.sendgrid.net");
        properties.setPort(587);
        properties.setAuth(true);
        properties.setUsername("apikey");
        properties.setPassword("SG.dummy-key");
        properties.setStarttls(true);
        properties.setFromAddress("support@flowdesk.io");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation with valid production SSL configuration")
    void shouldPassValidProductionSslConfiguration() {
        EmailProperties properties = new EmailProperties();
        properties.setProduction(true);
        properties.setHost("smtp.sendgrid.net");
        properties.setPort(465);
        properties.setAuth(true);
        properties.setUsername("apikey");
        properties.setPassword("SG.dummy-key");
        properties.setSsl(true);
        properties.setFromAddress("support@flowdesk.io");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65536, 70000})
    @DisplayName("Should fail validation when port is outside range 1-65535")
    void shouldFailWhenPortOutOfRange(int invalidPort) {
        EmailProperties properties = new EmailProperties();
        properties.setPort(invalidPort);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mail.port must be between 1 and 65535");
    }

    @Test
    @DisplayName("Should fail validation when timeouts are non-positive")
    void shouldFailWhenTimeoutsAreNonPositive() {
        EmailProperties properties = new EmailProperties();
        properties.setConnectionTimeoutMs(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email timeouts must be strictly positive");
    }

    @Test
    @DisplayName("Should fail validation when timeouts exceed maximum limit of 60 seconds")
    void shouldFailWhenTimeoutsExceedLimit() {
        EmailProperties properties = new EmailProperties();
        properties.setTimeoutMs(60001);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email timeouts must not exceed 60000 ms");
    }

    @Test
    @DisplayName("Should fail validation when from-address is invalid")
    void shouldFailWhenFromAddressIsInvalid() {
        EmailProperties properties = new EmailProperties();
        properties.setFromAddress("invalid-from-address");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mail.from-address must be a valid email address");
    }

    @Test
    @DisplayName("Should fail validation when from-name contains illegal CRLF characters")
    void shouldFailWhenFromNameContainsCrLf() {
        EmailProperties properties = new EmailProperties();
        properties.setFromName("FlowDesk\r\nBcc: evil@test.com");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.mail.from-name contains illegal newline characters");
    }

    @Test
    @DisplayName("Should bypass validation checks when email is disabled")
    void shouldBypassValidationWhenDisabled() {
        EmailProperties properties = new EmailProperties();
        properties.setEnabled(false);
        properties.setProduction(true);
        properties.setHost("localhost"); // would fail if enabled

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }
}
