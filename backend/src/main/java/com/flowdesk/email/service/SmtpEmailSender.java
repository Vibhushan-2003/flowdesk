package com.flowdesk.email.service;

import com.flowdesk.email.config.EmailProperties;
import com.flowdesk.email.exception.EmailDeliveryException;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Production-ready SMTP implementation of {@link EmailSender} utilizing Spring's
 * {@link JavaMailSender}.
 * <p>
 * Ensures safe logging (never exposing message bodies, tokens, or credentials), bounded timeouts,
 * graceful handling of disabled delivery, and clear differentiation of delivery outcomes.
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public EmailDeliveryResult send(EmailMessage message) throws EmailDeliveryException {
        Objects.requireNonNull(message, "EmailMessage must not be null");

        String maskedRecipient = EmailMessage.maskEmail(message.getTo());

        // Fast bypass if email sending is disabled (e.g. CI / test / offline dev)
        if (!properties.isEnabled()) {
            log.debug("Outbound email delivery is disabled. Suppressed message to [{}] with subject [{}].",
                    maskedRecipient, message.getSubject());
            return EmailDeliveryResult.disabled(message.getTo());
        }

        String messageId = UUID.randomUUID().toString();

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    message.hasHtml(),
                    StandardCharsets.UTF_8.name()
            );

            InternetAddress fromAddress = new InternetAddress(
                    properties.getFromAddress(),
                    properties.getFromName(),
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress);
            helper.setTo(message.getTo());
            helper.setSubject(message.getSubject());

            if (message.hasHtml()) {
                helper.setText(message.getTextBody(), message.getHtmlBody());
            } else {
                helper.setText(message.getTextBody(), false);
            }

            if (message.getReplyTo() != null) {
                helper.setReplyTo(message.getReplyTo());
            }

            mimeMessage.setHeader("X-FlowDesk-Message-ID", messageId);

            log.debug("Dispatching outbound email [id={}] to [{}] with subject [{}] via SMTP host [{}:{}]...",
                    messageId, maskedRecipient, message.getSubject(), properties.getHost(), properties.getPort());

            mailSender.send(mimeMessage);

            log.info("Email [id={}] successfully accepted for delivery to [{}] with subject [{}].",
                    messageId, maskedRecipient, message.getSubject());

            return EmailDeliveryResult.accepted(messageId, message.getTo());

        } catch (MailException ex) {
            String errorSummary = "SMTP transport failure sending to " + maskedRecipient + ": " + ex.getClass().getSimpleName();
            log.error("Outbound email delivery failed for [id={}] to [{}]: {}",
                    messageId, maskedRecipient, ex.getMessage());
            throw new EmailDeliveryException(errorSummary, ex);

        } catch (MessagingException | UnsupportedEncodingException ex) {
            String errorSummary = "Message composition failure for " + maskedRecipient + ": " + ex.getMessage();
            log.error("MIME composition failed for [id={}] to [{}]: {}",
                    messageId, maskedRecipient, ex.getMessage());
            throw new EmailDeliveryException(errorSummary, ex);
        }
    }
}
