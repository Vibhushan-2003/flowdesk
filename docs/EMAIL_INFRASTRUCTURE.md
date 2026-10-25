# FlowDesk Outbound Email Infrastructure

## Overview

FlowDesk Phase 2A introduces a small, secure, reusable outbound email infrastructure using Spring Boot's mail subsystem (`spring-boot-starter-mail`).

This infrastructure provides the foundational delivery and templating services for upcoming security-critical operations, including:
- Self-service password reset emails (Phase 2B)
- Administrator-initiated employee invitations (Phase 2B)
- Account activation and welcome notifications
- Security alerts (e.g. password changed, session revoked)

---

## Architecture & Components

The email infrastructure lives under package `com.flowdesk.email`:

```
com.flowdesk.email
├── config
│   ├── EmailConfig.java               # Configures JavaMailSender & EmailSender beans
│   └── EmailProperties.java           # @ConfigurationProperties(prefix = "app.mail") with production guards
├── exception
│   ├── EmailDeliveryException.java    # Safe runtime exception for transport/composition failures
│   └── InvalidEmailException.java     # Thrown on recipient or header injection validation failure
├── model
│   ├── EmailDeliveryResult.java       # Record returning status (ACCEPTED, FAILED, DISABLED), messageId, timestamp
│   ├── EmailDeliveryStatus.java       # Enum: ACCEPTED, FAILED, DISABLED
│   └── EmailMessage.java              # Validated immutable outgoing email model with header injection guards
├── service
│   ├── EmailSender.java               # Core delivery interface
│   └── SmtpEmailSender.java           # SMTP implementation wrapping JavaMailSender
└── template
    └── EmailTemplateService.java      # Branded HTML + readable plain-text template generator
```

### Key Design Principles:
1. **Decoupled Delivery Abstraction:** `EmailSender` is completely decoupled from authentication, ticket workflows, or database entities.
2. **Strict Header Injection Prevention:** `EmailMessage` validates all recipient, subject, and reply-to strings, rejecting any carriage return (`\r`) or line feed (`\n`) characters.
3. **Safe Logging & Token Hygiene:** `EmailMessage.toString()` and `SmtpEmailSender` sanitize log statements. Recipient emails are masked (e.g., `u***r@flowdesk.test`), and message bodies containing security tokens are **never** logged.
4. **Resilient Local & Test Execution:** In automated tests (`application-test.properties`), `app.mail.enabled=false` suppresses outbound network traffic so test suites run independently without requiring a live SMTP server.
5. **Fail-Safe Production Guardrails (Fail-Closed):** Whenever `app.mail.production=true` or running under `prod`/`production` Spring profiles, `EmailProperties` strictly requires STARTTLS or SSL encryption, validates port ranges and bounded positive timeouts, forbids localhost/loopback hosts, and requires non-blank credentials for authenticated relays.

---

## Configuration & Environment Variables

Outbound email delivery is driven entirely by environment variables. The table below outlines the available configuration properties in `backend/src/main/resources/application.properties`:

| Property Key | Environment Variable | Default (Local Dev) | Description |
| :--- | :--- | :---: | :--- |
| `app.mail.enabled` | `FLOWDESK_MAIL_ENABLED` | `true` | Master switch. When `false`, delivery is safely suppressed and returns status `DISABLED`. |
| `app.mail.host` | `SMTP_HOST` | `localhost` | SMTP relay server hostname or IP address. |
| `app.mail.port` | `SMTP_PORT` | `1025` | SMTP server port (1025 for local Mailpit, 587 for STARTTLS, 465 for SSL). |
| `app.mail.username` | `SMTP_USERNAME` | `""` | SMTP username (leave blank for local unauthenticated Mailpit). |
| `app.mail.password` | `SMTP_PASSWORD` | `""` | SMTP password / API token. |
| `app.mail.auth` | `SMTP_AUTH` | `false` | Whether SMTP authentication (`AUTH LOGIN`/`PLAIN`) is enabled. |
| `app.mail.starttls` | `SMTP_STARTTLS` | `false` | Whether `STARTTLS` negotiation is required. |
| `app.mail.ssl` | `SMTP_SSL` | `false` | Whether direct SSL/TLS socket encryption is enabled. |
| `app.mail.connection-timeout-ms` | `SMTP_CONNECTION_TIMEOUT_MS` | `5000` | Bounded socket connection timeout (milliseconds). |
| `app.mail.timeout-ms` | `SMTP_TIMEOUT_MS` | `5000` | Bounded socket read timeout (milliseconds). |
| `app.mail.write-timeout-ms` | `SMTP_WRITE_TIMEOUT_MS` | `5000` | Bounded socket write timeout (milliseconds). |
| `app.mail.from-address` | `FLOWDESK_MAIL_FROM_ADDRESS` | `noreply@flowdesk.internal` | Validated 'From' email address. |
| `app.mail.from-name` | `FLOWDESK_MAIL_FROM_NAME` | `FlowDesk` | Display sender name. |
| `app.mail.production` | `FLOWDESK_MAIL_PRODUCTION` | `false` | Set to `true` in production to enforce strict security checks. |

---

## Local Development with Mailpit

FlowDesk uses [Mailpit](https://github.com/axllent/mailpit) for local email inspection. Mailpit acts as a mock SMTP server that intercepts outbound messages and displays them in a web interface.

### 1. Ports
- **SMTP Port:** `127.0.0.1:1025`
- **Web UI:** `http://localhost:8025`

### 2. Starting Mailpit Container
If the `flowdesk-mailpit` container is not running, start it with Docker:

```bash
docker run -d \
  --name flowdesk-mailpit \
  --restart unless-stopped \
  -p 1025:1025 \
  -p 8025:8025 \
  axllent/mailpit
```

### 3. Inspecting Intercepted Emails
1. Open your browser to `http://localhost:8025`.
2. All emails sent by FlowDesk in development appear in the inbox.
3. You can inspect:
   - Recipient and sender headers
   - Responsive HTML preview
   - Raw plain-text body
   - MIME source headers (including `X-FlowDesk-Message-ID`)

---

## Switching to a Production SMTP Provider

To switch from local Mailpit to a production SMTP provider (such as Amazon SES, SendGrid, or Mailgun):

1. Set `FLOWDESK_MAIL_PRODUCTION=true`.
2. Supply your provider's credentials and endpoints via environment variables.

### Example: Amazon SES (Simple Email Service)
```bash
FLOWDESK_MAIL_PRODUCTION=true
FLOWDESK_MAIL_ENABLED=true
SMTP_HOST=email-smtp.us-east-1.amazonaws.com
SMTP_PORT=587
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_USERNAME=AKIAIOSFODNN7EXAMPLE
SMTP_PASSWORD=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
FLOWDESK_MAIL_FROM_ADDRESS=notifications@yourdomain.com
FLOWDESK_MAIL_FROM_NAME="FlowDesk System"
```

### Example: SendGrid
```bash
FLOWDESK_MAIL_PRODUCTION=true
FLOWDESK_MAIL_ENABLED=true
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_USERNAME=apikey
SMTP_PASSWORD=SG.your_actual_api_key_here
FLOWDESK_MAIL_FROM_ADDRESS=no-reply@yourdomain.com
FLOWDESK_MAIL_FROM_NAME="FlowDesk Support"
```

### Production Validation Guardrails (Fail-Closed)
When running in production (either explicitly via `FLOWDESK_MAIL_PRODUCTION=true` or automatically when active Spring profiles include `prod` or `production`), FlowDesk's `EmailProperties.validate()` fails closed and refuses application startup if:
- **Missing Encryption:** Both STARTTLS (`app.mail.starttls`) and SSL (`app.mail.ssl`) are disabled. Unencrypted SMTP is strictly forbidden in production.
- **Localhost / Loopback:** `app.mail.host` is configured to `localhost`, `127.0.0.1`, `::1`, `0.0.0.0`, or `.localhost`.
- **Missing Credentials:** `app.mail.auth=true` but `username` or `password` are blank.
- **Port Out of Range:** `app.mail.port` is outside the valid range of `1` to `65535`.
- **Invalid or Unbounded Timeouts:** Any timeout (`connection-timeout-ms`, `timeout-ms`, `write-timeout-ms`) is non-positive or exceeds `60000` ms (to prevent socket / thread starvation).
- **Invalid Sender Configuration:** `app.mail.from-address` is not a valid email address, or `app.mail.from-name` contains illegal CRLF characters.

### Transport Security & Downgrade Prevention
In `EmailConfig.java`, Spring's JavaMailSender transport is hardened to prevent MITM and plaintext downgrade attacks:
- `mail.smtp.starttls.enable=true` and `mail.smtp.starttls.required=true`: The SMTP client explicitly requires TLS negotiation. If the remote relay server attempts to strip or downgrade the `STARTTLS` handshake, connection establishment immediately aborts.
- `mail.smtp.ssl.checkserveridentity=true`: Enforces RFC 2595 endpoint certificate verification, ensuring the remote server certificate matches the configured `app.mail.host`.

---

## Reusing Email Infrastructure in Future Services

Future features (Phase 2B Password Reset, Invitations) can reuse this infrastructure by injecting `EmailSender` and `EmailTemplateService`:

```java
@Service
public class PasswordResetService {

    private final EmailSender emailSender;
    private final EmailTemplateService emailTemplateService;

    public PasswordResetService(EmailSender emailSender, EmailTemplateService emailTemplateService) {
        this.emailSender = emailSender;
        this.emailTemplateService = emailTemplateService;
    }

    public void dispatchPasswordReset(String recipientEmail, String resetToken) {
        String resetUrl = "https://flowdesk.example.com/reset-password?token=" + resetToken;
        Duration validity = Duration.ofMinutes(15);

        // 1. Build branded, safe EmailMessage
        EmailMessage message = emailTemplateService.buildPasswordResetEmail(recipientEmail, resetUrl, validity);

        // 2. Dispatch via EmailSender
        try {
            EmailDeliveryResult result = emailSender.send(message);
            // Result provides messageId, timestamp, and status (ACCEPTED / DISABLED)
        } catch (EmailDeliveryException ex) {
            // Log transport failure without leaking tokens; provide clean API error to caller
            log.error("Unable to dispatch password reset email to user: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery service unavailable");
        }
    }
}
```

---

## Security Limitations & Remaining Scope

1. **Synchronous In-Memory Delivery (Phase 2A):**
   Sending currently executes synchronously within the request thread. Connection timeouts are strictly capped at 5000ms. In high-volume production deployments, an asynchronous task queue or outbox worker pattern should be introduced.
2. **Transport Error Masking:**
   Raw socket exception messages and SMTP credentials are intercepted to prevent exposure to end users or API consumers.
3. **No Direct Public Endpoints:**
   No controller endpoints or public routes are created in this phase. Email sending is only callable internally by backend services.
