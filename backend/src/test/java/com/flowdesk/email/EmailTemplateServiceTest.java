package com.flowdesk.email;

import com.flowdesk.email.model.EmailMessage;
import com.flowdesk.email.template.EmailTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateServiceTest {

    private EmailTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new EmailTemplateService();
    }

    @Test
    @DisplayName("Should build valid password reset email with plain text and branded HTML")
    void shouldBuildPasswordResetEmail() {
        String recipient = "bob@flowdesk.test";
        String resetUrl = "http://localhost:5173/reset-password?token=sample-reset-token-12345";
        Duration expiration = Duration.ofMinutes(20);

        EmailMessage message = templateService.buildPasswordResetEmail(recipient, resetUrl, expiration);

        assertThat(message.getTo()).isEqualTo(recipient);
        assertThat(message.getSubject()).isEqualTo("Reset your FlowDesk password");
        assertThat(message.hasHtml()).isTrue();

        // Plain-text checks
        assertThat(message.getTextBody())
                .contains("FlowDesk Password Reset")
                .contains(recipient)
                .contains(resetUrl)
                .contains("20 minutes")
                .contains("This link can only be used once")
                .contains("FlowDesk Automated Security System");

        // HTML checks
        assertThat(message.getHtmlBody())
                .contains("FlowDesk")
                .contains("Password Reset Request")
                .contains(recipient)
                .contains("href=\"" + resetUrl + "\"")
                .contains("20 minutes")
                .contains("Please do not reply directly to this email");
    }

    @Test
    @DisplayName("Should escape HTML in parameters to prevent XSS / HTML injection")
    void shouldEscapeHtmlInTemplates() {
        String recipient = "attacker@flowdesk.test";
        String maliciousUrl = "http://localhost:5173/reset?token=\"><script>alert('xss')</script>";
        Duration expiration = Duration.ofMinutes(15);

        EmailMessage message = templateService.buildPasswordResetEmail(recipient, maliciousUrl, expiration);

        assertThat(message.getHtmlBody()).doesNotContain("<script>alert('xss')</script>");
        assertThat(message.getHtmlBody()).contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
    }

    @Test
    @DisplayName("Should build user invitation email with proper metadata")
    void shouldBuildUserInvitationEmail() {
        String recipient = "newemployee@flowdesk.test";
        String inviter = "Admin Alice";
        String setupUrl = "http://localhost:5173/activate-account?invitation=invite-token-abc";
        Duration expiration = Duration.ofHours(48);

        EmailMessage message = templateService.buildUserInvitationEmail(recipient, inviter, setupUrl, expiration);

        assertThat(message.getTo()).isEqualTo(recipient);
        assertThat(message.getSubject()).isEqualTo("You have been invited to join FlowDesk");
        assertThat(message.getTextBody())
                .contains(inviter)
                .contains(setupUrl)
                .contains("48 hours");
        assertThat(message.getHtmlBody())
                .contains(inviter)
                .contains("Activate Account")
                .contains("48 hours");
    }

    @Test
    @DisplayName("Should build security notice email with alert layout")
    void shouldBuildSecurityNoticeEmail() {
        String recipient = "alice@flowdesk.test";
        String title = "Password Successfully Changed";
        String details = "Your account password was updated from IP address 192.168.1.50 at 12:00 UTC.";

        EmailMessage message = templateService.buildSecurityNoticeEmail(recipient, title, details);

        assertThat(message.getTo()).isEqualTo(recipient);
        assertThat(message.getSubject()).isEqualTo("FlowDesk Security Notice: Password Successfully Changed");
        assertThat(message.getTextBody())
                .contains(title)
                .contains(details)
                .contains("contact your administrator or security team immediately");
        assertThat(message.getHtmlBody())
                .contains(title)
                .contains(details)
                .contains("#fef2f2"); // alert container background
    }

    @Test
    @DisplayName("Should build generic notification email")
    void shouldBuildGenericNotificationEmail() {
        String recipient = "user@flowdesk.test";
        String subject = "System Maintenance Notification";
        String headline = "Scheduled Maintenance Window";
        String body = "FlowDesk will undergo scheduled maintenance tonight at 02:00 UTC.";

        EmailMessage message = templateService.buildNotificationEmail(recipient, subject, headline, body);

        assertThat(message.getTo()).isEqualTo(recipient);
        assertThat(message.getSubject()).isEqualTo(subject);
        assertThat(message.getTextBody()).contains(headline).contains(body);
        assertThat(message.getHtmlBody()).contains(headline).contains(body);
    }
}
