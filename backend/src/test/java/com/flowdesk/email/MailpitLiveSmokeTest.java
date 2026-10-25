package com.flowdesk.email;

import com.flowdesk.email.config.EmailConfig;
import com.flowdesk.email.config.EmailProperties;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailDeliveryStatus;
import com.flowdesk.email.model.EmailMessage;
import com.flowdesk.email.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live SMTP smoke test against a local Mailpit container at 127.0.0.1:1025.
 * <p>
 * This test is enabled explicitly via system property {@code -Dsmoke.mailpit=true}
 * to ensure that regular automated builds do not depend on an external running container.
 * <p>
 * Command to run manual smoke test:
 * <pre>
 *   .\mvnw.cmd test -Dsmoke.mailpit=true -Dtest=MailpitLiveSmokeTest
 * </pre>
 */
@EnabledIfSystemProperty(named = "smoke.mailpit", matches = "true")
class MailpitLiveSmokeTest {

    private EmailSender emailSender;
    private EmailProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EmailProperties();
        properties.setEnabled(true);
        properties.setHost("127.0.0.1");
        properties.setPort(1025);
        properties.setAuth(false);
        properties.setStarttls(false);
        properties.setConnectionTimeoutMs(10000);
        properties.setTimeoutMs(10000);
        properties.setWriteTimeoutMs(10000);
        properties.setFromAddress("noreply@flowdesk.internal");
        properties.setFromName("FlowDesk Smoke Test");

        EmailConfig config = new EmailConfig();
        JavaMailSender javaMailSender = config.javaMailSender(properties);
        emailSender = config.emailSender(javaMailSender, properties);
    }

    @Test
    @DisplayName("Should successfully deliver harmless smoke-test email to local Mailpit")
    void shouldDeliverSmokeTestEmailToMailpit() {
        EmailMessage message = EmailMessage.builder()
                .to("smoke-test@flowdesk.test")
                .subject("FlowDesk SMTP Infrastructure Smoke Test")
                .textBody("This is an automated smoke test verifying local Mailpit SMTP delivery.")
                .htmlBody("<p>This is an automated smoke test verifying local Mailpit SMTP delivery.</p>")
                .build();

        EmailDeliveryResult result = emailSender.send(message);

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.ACCEPTED);
        assertThat(result.messageId()).isNotBlank();
        assertThat(result.recipient()).isEqualTo("smoke-test@flowdesk.test");
    }
}
