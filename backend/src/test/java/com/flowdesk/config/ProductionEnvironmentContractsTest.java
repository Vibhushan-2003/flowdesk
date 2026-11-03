package com.flowdesk.config;

import com.flowdesk.ai.client.GeminiAiReplyDraftClient;
import com.flowdesk.ai.client.GeminiAiResolutionSummaryClient;
import com.flowdesk.ai.client.GeminiAiSupportCopilotClient;
import com.flowdesk.ai.client.GeminiAiTriageClient;
import com.flowdesk.auth.config.PasswordResetProperties;
import com.flowdesk.email.config.EmailProperties;
import com.flowdesk.user.config.UserInvitationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates production environment contracts, fail-fast validations,
 * and optional integration behaviors according to Day 65 specifications.
 */
@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=${DB_TEST_URL:jdbc:postgresql://localhost:5432/flowdesk_test}",
        "spring.datasource.username=${DB_TEST_USERNAME:${DB_USERNAME:flowdesk_app}}",
        "spring.datasource.password=${DB_TEST_PASSWORD:${DB_PASSWORD:12345678}}",
        "app.security.jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=",
        "FRONTEND_ORIGIN=https://flowdesk.test.internal",
        "FLOWDESK_FRONTEND_ORIGIN=https://flowdesk.test.internal",
        "app.cors.allowed-origin=https://flowdesk.test.internal",
        "app.security.password-reset.frontend-origin=https://flowdesk.test.internal",
        "app.security.invitation.frontend-origin=https://flowdesk.test.internal",
        "app.mail.enabled=false",
        "app.mail.host=smtp.test.internal",
        "app.storage.attachment.local.base-directory=${java.io.tmpdir}/flowdesk-prod-test-contracts",
        "app.attachment.malware-scanner.type=clamav",
        "app.attachment.malware-scanner.clamav.host=localhost",
        "app.attachment.malware-scanner.clamav.port=3310",
        // Crucial test contract: GEMINI_API_KEY is left completely empty
        "GEMINI_API_KEY=",
        "GOOGLE_OAUTH_ENABLED=false"
})
class ProductionEnvironmentContractsTest {

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private GeminiAiTriageClient geminiAiTriageClient;

    @Autowired(required = false)
    private GeminiAiSupportCopilotClient geminiAiSupportCopilotClient;

    @Autowired(required = false)
    private GeminiAiReplyDraftClient geminiAiReplyDraftClient;

    @Autowired(required = false)
    private GeminiAiResolutionSummaryClient geminiAiResolutionSummaryClient;

    @Test
    @DisplayName("Production starts cleanly without Gemini API key when AI features are dormant")
    void productionStartsWithoutGeminiApiKey() {
        // Context loads cleanly with empty GEMINI_API_KEY
        assertThat(environment.getProperty("app.ai.gemini.api-key")).isEmpty();

        assertThat(geminiAiTriageClient).isNotNull();
        assertThat(geminiAiSupportCopilotClient).isNotNull();
        assertThat(geminiAiReplyDraftClient).isNotNull();
        assertThat(geminiAiResolutionSummaryClient).isNotNull();
    }

    @Test
    @DisplayName("Production rejects non-HTTPS frontend origin for password reset")
    void productionRejectsNonHttpsPasswordResetOrigin() {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setProduction(true);
        properties.setFrontendOrigin("http://insecure.flowdesk.com");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production password reset requires an HTTPS frontend origin");
    }

    @Test
    @DisplayName("Production rejects non-HTTPS frontend origin for user invitations")
    void productionRejectsNonHttpsInvitationOrigin() {
        UserInvitationProperties properties = new UserInvitationProperties();
        properties.setProduction(true);
        properties.setFrontendOrigin("http://insecure.flowdesk.com");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production invitation requires an HTTPS frontend origin");
    }

    @Test
    @DisplayName("Production accepts valid HTTPS frontend origins and strips trailing slashes")
    void productionAcceptsValidHttpsOrigins() {
        PasswordResetProperties resetProps = new PasswordResetProperties();
        resetProps.setProduction(true);
        resetProps.setFrontendOrigin("https://flowdesk.example.com///");
        resetProps.validate();
        assertThat(resetProps.getFrontendOrigin()).isEqualTo("https://flowdesk.example.com");

        UserInvitationProperties inviteProps = new UserInvitationProperties();
        inviteProps.setProduction(true);
        inviteProps.setFrontendOrigin("https://flowdesk.example.com/");
        inviteProps.validate();
        assertThat(inviteProps.getFrontendOrigin()).isEqualTo("https://flowdesk.example.com");
    }

    @Test
    @DisplayName("Production email properties enforce encryption and non-localhost host when enabled")
    void productionEmailEnforcesTlsAndNonLocalhost() {
        EmailProperties properties = new EmailProperties();
        properties.setProduction(true);
        properties.setEnabled(true);
        properties.setHost("localhost"); // Forbidden in production
        properties.setStarttls(false);
        properties.setSsl(false);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires a non-localhost SMTP host");

        properties.setHost("smtp-relay.brevo.com");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires STARTTLS or SSL/TLS encryption");

        // When STARTTLS is enabled with credentials and valid From address, validation passes
        properties.setPort(587);
        properties.setStarttls(true);
        properties.setAuth(true);
        properties.setUsername("brevo-user");
        properties.setPassword("secret-key");
        properties.setFromAddress("noreply@flowdesk.example.com");

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Production email properties cleanly bypass validation when app.mail.enabled=false")
    void productionEmailBypassesValidationWhenDisabled() {
        EmailProperties properties = new EmailProperties();
        properties.setProduction(true);
        properties.setEnabled(false);
        properties.setHost("localhost"); // Normally invalid, but allowed when disabled

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Database username mapping supports fallback from DB_USERNAME to DB_USER")
    void databaseUsernameMappingSupportsFallback() {
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setProperty("DB_USER", "postgres_service_user");

        String resolved = mockEnv.resolvePlaceholders("${DB_USERNAME:${DB_USER}}");
        assertThat(resolved).isEqualTo("postgres_service_user");

        mockEnv.setProperty("DB_USERNAME", "explicit_app_user");
        String resolvedExplicit = mockEnv.resolvePlaceholders("${DB_USERNAME:${DB_USER}}");
        assertThat(resolvedExplicit).isEqualTo("explicit_app_user");
    }
}

