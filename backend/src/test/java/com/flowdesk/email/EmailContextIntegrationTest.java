package com.flowdesk.email;

import com.flowdesk.email.config.EmailProperties;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailDeliveryStatus;
import com.flowdesk.email.model.EmailMessage;
import com.flowdesk.email.service.EmailSender;
import com.flowdesk.email.service.SmtpEmailSender;
import com.flowdesk.email.template.EmailTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EmailContextIntegrationTest {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private EmailProperties emailProperties;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Test
    @DisplayName("Should wire email beans into Spring Boot ApplicationContext")
    void shouldWireEmailBeans() {
        assertThat(javaMailSender).isNotNull();
        assertThat(emailSender).isNotNull();
        assertThat(emailSender).isInstanceOf(SmtpEmailSender.class);
        assertThat(emailProperties).isNotNull();
        assertThat(emailTemplateService).isNotNull();
    }

    @Test
    @DisplayName("In test profile, email should be disabled by default and safely suppress sending")
    void shouldSafelySuppressSendingInTestProfile() {
        assertThat(emailProperties.isEnabled()).isFalse();

        EmailMessage message = emailTemplateService.buildNotificationEmail(
                "dev@flowdesk.test",
                "Integration Test Subject",
                "Integration Test Headline",
                "Integration test message body."
        );

        EmailDeliveryResult result = emailSender.send(message);

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.DISABLED);
        assertThat(result.recipient()).isEqualTo("dev@flowdesk.test");
        assertThat(result.details()).contains("disabled");
    }
}
