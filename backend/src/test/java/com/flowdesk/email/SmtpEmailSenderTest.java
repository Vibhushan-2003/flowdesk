package com.flowdesk.email;

import com.flowdesk.email.config.EmailProperties;
import com.flowdesk.email.exception.EmailDeliveryException;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailDeliveryStatus;
import com.flowdesk.email.model.EmailMessage;
import com.flowdesk.email.service.SmtpEmailSender;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmtpEmailSenderTest {

    private JavaMailSender mailSender;
    private EmailProperties properties;
    private SmtpEmailSender emailSender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        properties = new EmailProperties();
        properties.setEnabled(true);
        properties.setHost("127.0.0.1");
        properties.setPort(1025);
        properties.setFromAddress("noreply@flowdesk.internal");
        properties.setFromName("FlowDesk System");

        // Use real MimeMessage instance for fidelity
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        emailSender = new SmtpEmailSender(mailSender, properties);
    }

    @Test
    @DisplayName("Should deliver valid plain-text email through SMTP transport")
    void shouldDeliverPlainTextEmail() throws Exception {
        EmailMessage message = EmailMessage.builder()
                .to("recipient@flowdesk.test")
                .subject("Welcome to FlowDesk")
                .textBody("This is a plain-text notification.")
                .build();

        EmailDeliveryResult result = emailSender.send(message);

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.ACCEPTED);
        assertThat(result.recipient()).isEqualTo("recipient@flowdesk.test");
        assertThat(result.messageId()).isNotBlank();
        assertThat(result.details()).contains("Accepted for delivery");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should deliver valid multipart HTML and plain-text email")
    void shouldDeliverMultipartHtmlEmail() {
        EmailMessage message = EmailMessage.builder()
                .to("recipient@flowdesk.test")
                .subject("HTML Notification")
                .textBody("Plain text fallback")
                .htmlBody("<p>Rich HTML content</p>")
                .replyTo("support@flowdesk.test")
                .build();

        EmailDeliveryResult result = emailSender.send(message);

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.ACCEPTED);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should suppress sending and return DISABLED status when email is disabled")
    void shouldSuppressSendingWhenDisabled() {
        properties.setEnabled(false);

        EmailMessage message = EmailMessage.of("recipient@flowdesk.test", "Subject", "Body");

        EmailDeliveryResult result = emailSender.send(message);

        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.DISABLED);
        assertThat(result.recipient()).isEqualTo("recipient@flowdesk.test");
        assertThat(result.details()).contains("disabled");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should handle MailSendException and throw EmailDeliveryException")
    void shouldHandleMailSendException() {
        doThrow(new MailSendException("Connection refused by SMTP server"))
                .when(mailSender).send(any(MimeMessage.class));

        EmailMessage message = EmailMessage.of("recipient@flowdesk.test", "Subject", "Body");

        assertThatThrownBy(() -> emailSender.send(message))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("SMTP transport failure")
                .hasCauseInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("Should handle MailAuthenticationException without leaking sensitive credentials")
    void shouldHandleAuthenticationExceptionSafely() {
        doThrow(new MailAuthenticationException("Authentication failed for user flowdesk_user"))
                .when(mailSender).send(any(MimeMessage.class));

        EmailMessage message = EmailMessage.of("recipient@flowdesk.test", "Subject", "Body");

        assertThatThrownBy(() -> emailSender.send(message))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("SMTP transport failure");
    }

    @Test
    @DisplayName("Should reject null EmailMessage")
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> emailSender.send(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("EmailMessage must not be null");
    }
}
