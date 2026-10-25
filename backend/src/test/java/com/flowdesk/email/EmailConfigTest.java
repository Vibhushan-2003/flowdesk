package com.flowdesk.email;

import com.flowdesk.email.config.EmailConfig;
import com.flowdesk.email.config.EmailProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class EmailConfigTest {

    private final EmailConfig config = new EmailConfig();

    @Test
    @DisplayName("Should configure JavaMailSender with STARTTLS required and certificate identity check to prevent downgrade")
    void shouldConfigureStarttlsRequiredAndIdentityCheck() {
        EmailProperties properties = new EmailProperties();
        properties.setHost("smtp.sendgrid.net");
        properties.setPort(587);
        properties.setStarttls(true);
        properties.setAuth(true);
        properties.setUsername("apikey");
        properties.setPassword("SG.test-secret-value");

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) config.javaMailSender(properties);
        Properties props = mailSender.getJavaMailProperties();

        assertThat(mailSender.getHost()).isEqualTo("smtp.sendgrid.net");
        assertThat(mailSender.getPort()).isEqualTo(587);
        assertThat(mailSender.getUsername()).isEqualTo("apikey");
        assertThat(mailSender.getPassword()).isEqualTo("SG.test-secret-value");

        // Assert STARTTLS required is set (cannot downgrade to plaintext)
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true");

        // Assert server identity check is enforced against MITM
        assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    @DisplayName("Should configure JavaMailSender with direct SSL and certificate identity check")
    void shouldConfigureDirectSslAndIdentityCheck() {
        EmailProperties properties = new EmailProperties();
        properties.setHost("email-smtp.us-east-1.amazonaws.com");
        properties.setPort(465);
        properties.setSsl(true);
        properties.setAuth(true);
        properties.setUsername("ses-user");
        properties.setPassword("ses-password");

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) config.javaMailSender(properties);
        Properties props = mailSender.getJavaMailProperties();

        assertThat(props.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    @DisplayName("Should configure unauthenticated plaintext for local Mailpit development without TLS")
    void shouldConfigureUnauthenticatedLocalMailpit() {
        EmailProperties properties = new EmailProperties();
        properties.setHost("localhost");
        properties.setPort(1025);
        properties.setAuth(false);
        properties.setStarttls(false);
        properties.setSsl(false);

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) config.javaMailSender(properties);
        Properties props = mailSender.getJavaMailProperties();

        assertThat(mailSender.getHost()).isEqualTo("localhost");
        assertThat(mailSender.getPort()).isEqualTo(1025);
        assertThat(mailSender.getUsername()).isNull();
        assertThat(mailSender.getPassword()).isNull();

        assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("false");
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("false");
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("false");
        assertThat(props.getProperty("mail.smtp.ssl.enable")).isNull();
        assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isNull();
    }

    @Test
    @DisplayName("Should configure bounded timeouts on JavaMail properties")
    void shouldConfigureBoundedTimeouts() {
        EmailProperties properties = new EmailProperties();
        properties.setConnectionTimeoutMs(7000);
        properties.setTimeoutMs(8000);
        properties.setWriteTimeoutMs(9000);

        JavaMailSenderImpl mailSender = (JavaMailSenderImpl) config.javaMailSender(properties);
        Properties props = mailSender.getJavaMailProperties();

        assertThat(props.getProperty("mail.smtp.connectiontimeout")).isEqualTo("7000");
        assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("8000");
        assertThat(props.getProperty("mail.smtp.writetimeout")).isEqualTo("9000");
    }
}
