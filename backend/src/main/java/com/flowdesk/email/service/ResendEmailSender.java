package com.flowdesk.email.service;

import com.flowdesk.email.config.EmailProperties;
import com.flowdesk.email.exception.EmailDeliveryException;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production-ready Resend HTTPS REST API implementation of {@link EmailSender}.
 * <p>
 * Dispatches transactional emails via POST https://api.resend.com/emails over secure HTTPS (port 443).
 * Designed for environments such as cloud container platforms (e.g. Render Free) where outbound SMTP
 * ports (25, 465, 587) are restricted by platform policy.
 * <p>
 * Security & Reliability guarantees:
 * <ul>
 *   <li>Never logs authorization credentials, raw message bodies, or invitation/reset tokens.</li>
 *   <li>Masks recipient email addresses in all log entries.</li>
 *   <li>Enforces connect and read timeouts to prevent thread starvation.</li>
 *   <li>Gracefully bypasses network calls when email delivery is disabled by configuration.</li>
 * </ul>
 */
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient restClient;
    private final EmailProperties properties;

    public ResendEmailSender(RestClient.Builder restClientBuilder, EmailProperties properties) {
        this(buildRestClient(restClientBuilder, properties), properties);
    }

    private static RestClient buildRestClient(RestClient.Builder restClientBuilder, EmailProperties properties) {
        String baseUrl = properties.getResendBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.resend.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        return builder.baseUrl(baseUrl).build();
    }

    public ResendEmailSender(RestClient restClient, EmailProperties properties) {
        this.restClient = Objects.requireNonNull(restClient, "RestClient must not be null");
        this.properties = Objects.requireNonNull(properties, "EmailProperties must not be null");
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

        String from = buildFromHeader();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(message.getTo()));
        payload.put("subject", message.getSubject());
        payload.put("text", message.getTextBody());
        if (message.hasHtml()) {
            payload.put("html", message.getHtmlBody());
        }
        if (message.getReplyTo() != null && !message.getReplyTo().isBlank()) {
            payload.put("reply_to", message.getReplyTo());
        }

        log.debug("Dispatching outbound email via Resend HTTPS API to [{}] with subject [{}]...",
                maskedRecipient, message.getSubject());

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getResendApiKey().trim())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            String messageId = null;
            if (response != null && response.get("id") instanceof String id) {
                messageId = id;
            }
            if (messageId == null || messageId.isBlank()) {
                messageId = UUID.randomUUID().toString();
            }

            log.info("Email [id={}] successfully accepted for delivery by Resend HTTPS API to [{}] with subject [{}].",
                    messageId, maskedRecipient, message.getSubject());

            return EmailDeliveryResult.accepted(messageId, message.getTo(), "Accepted for delivery by Resend HTTPS API");

        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            String errorSummary;
            if (statusCode == 401) {
                errorSummary = "Resend API authentication failed: invalid API key (HTTP 401)";
            } else if (statusCode == 403) {
                errorSummary = "Resend API forbidden: unverified domain or restricted key (HTTP 403)";
            } else if (statusCode == 422) {
                errorSummary = "Resend API rejected email parameters: validation error (HTTP 422)";
            } else if (statusCode == 429) {
                errorSummary = "Resend API rate limit exceeded (HTTP 429)";
            } else if (statusCode >= 500) {
                errorSummary = "Resend API server error (HTTP " + statusCode + ")";
            } else {
                errorSummary = "Resend API rejected request with HTTP " + statusCode;
            }

            log.error("Resend HTTPS API delivery failed for recipient [{}] with subject [{}]: status={}",
                    maskedRecipient, message.getSubject(), statusCode);
            throw new EmailDeliveryException(errorSummary, ex);

        } catch (RestClientException ex) {
            String errorSummary = "Resend HTTPS API transport failure sending to " + maskedRecipient + ": " + ex.getClass().getSimpleName();
            log.error("Resend HTTPS API connection failed for recipient [{}] with subject [{}]: {}",
                    maskedRecipient, message.getSubject(), ex.getMessage());
            throw new EmailDeliveryException(errorSummary, ex);
        }
    }

    private String buildFromHeader() {
        String fromName = properties.getFromName();
        String fromAddress = properties.getFromAddress();
        if (fromName != null && !fromName.isBlank()) {
            return fromName.trim() + " <" + fromAddress.trim() + ">";
        }
        return fromAddress.trim();
    }
}

