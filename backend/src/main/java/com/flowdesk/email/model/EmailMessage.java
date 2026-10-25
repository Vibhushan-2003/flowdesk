package com.flowdesk.email.model;

import com.flowdesk.email.exception.InvalidEmailException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validated immutable representation of an outbound email message.
 * <p>
 * Enforces recipient formatting, strict header injection prevention (rejecting CR/LF in headers),
 * mandatory plain-text content, and masked {@link #toString()} to prevent accidental leakage
 * of security tokens or message bodies into logs.
 */
public final class EmailMessage {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,15}$"
    );

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_SUBJECT_LENGTH = 500;

    private final String to;
    private final String subject;
    private final String textBody;
    private final String htmlBody;
    private final String replyTo;

    private EmailMessage(Builder builder) {
        this.to = validateRecipient(builder.to, "Recipient email ('to')");
        this.subject = validateHeaderValue(builder.subject, "Subject", MAX_SUBJECT_LENGTH);
        this.textBody = validateBody(builder.textBody, "Plain-text body");
        this.htmlBody = builder.htmlBody != null && !builder.htmlBody.isBlank() ? builder.htmlBody : null;
        this.replyTo = builder.replyTo != null && !builder.replyTo.isBlank()
                ? validateRecipient(builder.replyTo, "Reply-to email")
                : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EmailMessage of(String to, String subject, String textBody) {
        return builder().to(to).subject(subject).textBody(textBody).build();
    }

    public static EmailMessage of(String to, String subject, String textBody, String htmlBody) {
        return builder().to(to).subject(subject).textBody(textBody).htmlBody(htmlBody).build();
    }

    public String getTo() {
        return to;
    }

    public String getSubject() {
        return subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public boolean hasHtml() {
        return htmlBody != null && !htmlBody.isBlank();
    }

    private static String validateRecipient(String email, String fieldName) {
        if (email == null || email.isBlank()) {
            throw new InvalidEmailException(fieldName + " must not be null or blank");
        }
        String trimmed = email.trim();
        if (trimmed.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidEmailException(fieldName + " exceeds maximum length of " + MAX_EMAIL_LENGTH + " characters");
        }
        if (containsCrLf(trimmed)) {
            throw new InvalidEmailException(fieldName + " contains illegal carriage return or newline characters (header injection detected)");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidEmailException(fieldName + " has an invalid email format: " + maskEmail(trimmed));
        }
        return trimmed;
    }

    private static String validateHeaderValue(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidEmailException(fieldName + " must not be null or blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidEmailException(fieldName + " exceeds maximum length of " + maxLength + " characters");
        }
        if (containsCrLf(trimmed)) {
            throw new InvalidEmailException(fieldName + " contains illegal carriage return or newline characters (header injection detected)");
        }
        return trimmed;
    }

    private static String validateBody(String body, String fieldName) {
        if (body == null || body.isBlank()) {
            throw new InvalidEmailException(fieldName + " must not be null or blank");
        }
        return body;
    }

    private static boolean containsCrLf(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    /**
     * Masks an email address for safe logging (e.g. j***e@example.com).
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<empty>";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***@" + (atIndex >= 0 ? email.substring(atIndex + 1) : "unknown");
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        if (local.length() <= 2) {
            return local.charAt(0) + "***@" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }

    @Override
    public String toString() {
        // Crucial security control: Do NOT print message body or tokens in toString()
        return "EmailMessage[to=" + maskEmail(to)
                + ", subject=" + subject
                + ", hasHtml=" + hasHtml()
                + ", replyTo=" + (replyTo != null ? maskEmail(replyTo) : "none")
                + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailMessage that = (EmailMessage) o;
        return Objects.equals(to, that.to) &&
                Objects.equals(subject, that.subject) &&
                Objects.equals(textBody, that.textBody) &&
                Objects.equals(htmlBody, that.htmlBody) &&
                Objects.equals(replyTo, that.replyTo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(to, subject, textBody, htmlBody, replyTo);
    }

    public static final class Builder {
        private String to;
        private String subject;
        private String textBody;
        private String htmlBody;
        private String replyTo;

        private Builder() {}

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        public EmailMessage build() {
            return new EmailMessage(this);
        }
    }
}
