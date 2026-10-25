package com.flowdesk.email;

import com.flowdesk.email.exception.InvalidEmailException;
import com.flowdesk.email.model.EmailMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailMessageTest {

    @Test
    @DisplayName("Should successfully construct valid EmailMessage")
    void shouldConstructValidMessage() {
        EmailMessage message = EmailMessage.builder()
                .to("user@flowdesk.test")
                .subject("Test Subject")
                .textBody("Hello plain text body")
                .htmlBody("<p>Hello HTML body</p>")
                .replyTo("support@flowdesk.test")
                .build();

        assertThat(message.getTo()).isEqualTo("user@flowdesk.test");
        assertThat(message.getSubject()).isEqualTo("Test Subject");
        assertThat(message.getTextBody()).isEqualTo("Hello plain text body");
        assertThat(message.getHtmlBody()).isEqualTo("<p>Hello HTML body</p>");
        assertThat(message.getReplyTo()).isEqualTo("support@flowdesk.test");
        assertThat(message.hasHtml()).isTrue();
    }

    @Test
    @DisplayName("Should construct plain-text only message when HTML is absent")
    void shouldConstructPlainTextOnlyMessage() {
        EmailMessage message = EmailMessage.of("employee@flowdesk.test", "Welcome", "Welcome to FlowDesk");

        assertThat(message.getTo()).isEqualTo("employee@flowdesk.test");
        assertThat(message.getSubject()).isEqualTo("Welcome");
        assertThat(message.getTextBody()).isEqualTo("Welcome to FlowDesk");
        assertThat(message.getHtmlBody()).isNull();
        assertThat(message.hasHtml()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "invalid", "user@", "@domain.com", "user @domain.com", "user@domain"})
    @DisplayName("Should reject invalid recipient email format")
    void shouldRejectInvalidRecipientFormat(String invalidEmail) {
        assertThatThrownBy(() -> EmailMessage.builder()
                .to(invalidEmail)
                .subject("Subject")
                .textBody("Body")
                .build())
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    @DisplayName("Should reject null recipient")
    void shouldRejectNullRecipient() {
        assertThatThrownBy(() -> EmailMessage.builder()
                .to(null)
                .subject("Subject")
                .textBody("Body")
                .build())
                .isInstanceOf(InvalidEmailException.class)
                .hasMessageContaining("Recipient email ('to') must not be null or blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "user@flowdesk.test\r\nBcc: attacker@evil.test",
            "user@flowdesk.test\nBcc: attacker@evil.test",
            "user@flowdesk.test\rBcc: attacker@evil.test"
    })
    @DisplayName("Should reject header injection in recipient email")
    void shouldRejectHeaderInjectionInRecipient(String injectedRecipient) {
        assertThatThrownBy(() -> EmailMessage.builder()
                .to(injectedRecipient)
                .subject("Subject")
                .textBody("Body")
                .build())
                .isInstanceOf(InvalidEmailException.class)
                .hasMessageContaining("header injection detected");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Subject\r\nBcc: attacker@evil.test",
            "Subject\nInjected-Header: value",
            "Subject\rInjected"
    })
    @DisplayName("Should reject header injection in subject")
    void shouldRejectHeaderInjectionInSubject(String injectedSubject) {
        assertThatThrownBy(() -> EmailMessage.builder()
                .to("user@flowdesk.test")
                .subject(injectedSubject)
                .textBody("Body")
                .build())
                .isInstanceOf(InvalidEmailException.class)
                .hasMessageContaining("header injection detected");
    }

    @Test
    @DisplayName("Should reject empty or blank subject")
    void shouldRejectBlankSubject() {
        assertThatThrownBy(() -> EmailMessage.builder()
                .to("user@flowdesk.test")
                .subject("   ")
                .textBody("Body")
                .build())
                .isInstanceOf(InvalidEmailException.class)
                .hasMessageContaining("Subject must not be null or blank");
    }

    @Test
    @DisplayName("Should reject empty or blank body")
    void shouldRejectBlankBody() {
        assertThatThrownBy(() -> EmailMessage.builder()
                .to("user@flowdesk.test")
                .subject("Subject")
                .textBody("   ")
                .build())
                .isInstanceOf(InvalidEmailException.class)
                .hasMessageContaining("Plain-text body must not be null or blank");
    }

    @Test
    @DisplayName("Should reject header injection in reply-to")
    void shouldRejectHeaderInjectionInReplyTo() {
        assertThatThrownBy(() -> EmailMessage.builder()
                .to("user@flowdesk.test")
                .subject("Subject")
                .textBody("Body")
                .replyTo("support@flowdesk.test\r\nBcc: evil@test.com")
                .build())
                .isInstanceOf(InvalidEmailException.class)
                .hasMessageContaining("header injection detected");
    }

    @Test
    @DisplayName("toString() must never contain email body or sensitive reset tokens")
    void toStringMustNotExposeBodyOrTokens() {
        String sensitiveToken = "super-secret-token-abcdef-123456";
        EmailMessage message = EmailMessage.builder()
                .to("alice@flowdesk.test")
                .subject("Password Reset")
                .textBody("Your reset token is: " + sensitiveToken)
                .htmlBody("<p>Your reset token is: " + sensitiveToken + "</p>")
                .build();

        String stringRep = message.toString();

        assertThat(stringRep).doesNotContain(sensitiveToken);
        assertThat(stringRep).doesNotContain("Your reset token is");
        assertThat(stringRep).contains("a***e@flowdesk.test");
        assertThat(stringRep).contains("Password Reset");
    }

    @Test
    @DisplayName("maskEmail should properly mask sensitive address segments")
    void maskEmailShouldProperlyMask() {
        assertThat(EmailMessage.maskEmail("john.doe@company.com")).isEqualTo("j***e@company.com");
        assertThat(EmailMessage.maskEmail("ab@example.com")).isEqualTo("a***@example.com");
        assertThat(EmailMessage.maskEmail("a@example.com")).isEqualTo("***@example.com");
        assertThat(EmailMessage.maskEmail("")).isEqualTo("<empty>");
        assertThat(EmailMessage.maskEmail(null)).isEqualTo("<empty>");
    }
}
