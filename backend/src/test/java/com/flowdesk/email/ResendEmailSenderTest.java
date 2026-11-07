package com.flowdesk.email;

import com.flowdesk.email.config.EmailProperties;
import com.flowdesk.email.exception.EmailDeliveryException;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailDeliveryStatus;
import com.flowdesk.email.model.EmailMessage;
import com.flowdesk.email.service.ResendEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ResendEmailSenderTest {

    private EmailProperties properties;
    private MockRestServiceServer mockServer;
    private ResendEmailSender emailSender;

    @BeforeEach
    void setUp() {
        properties = new EmailProperties();
        properties.setEnabled(true);
        properties.setTransport("resend");
        properties.setResendApiKey("re_test_key_12345678");
        properties.setResendBaseUrl("https://api.resend.com");
        properties.setFromAddress("onboarding@resend.dev");
        properties.setFromName("FlowDesk System");

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        emailSender = new ResendEmailSender(restClientBuilder, properties);
    }

    @Test
    @DisplayName("Should successfully deliver plain-text email via Resend HTTPS API")
    void shouldDeliverPlainTextEmailViaResend() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_test_key_12345678"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.from").value("FlowDesk System <onboarding@resend.dev>"))
                .andExpect(jsonPath("$.to[0]").value("employee@flowdesk.test"))
                .andExpect(jsonPath("$.subject").value("Welcome to FlowDesk"))
                .andExpect(jsonPath("$.text").value("Please complete your account activation."))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\": \"resend-msg-98765\"}"));

        EmailMessage message = EmailMessage.builder()
                .to("employee@flowdesk.test")
                .subject("Welcome to FlowDesk")
                .textBody("Please complete your account activation.")
                .build();

        EmailDeliveryResult result = emailSender.send(message);

        mockServer.verify();
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.ACCEPTED);
        assertThat(result.messageId()).isEqualTo("resend-msg-98765");
        assertThat(result.recipient()).isEqualTo("employee@flowdesk.test");
        assertThat(result.details()).contains("Resend HTTPS API");
    }

    @Test
    @DisplayName("Should deliver multipart HTML email with reply-to via Resend HTTPS API")
    void shouldDeliverMultipartHtmlEmailWithReplyTo() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.from").value("FlowDesk System <onboarding@resend.dev>"))
                .andExpect(jsonPath("$.to[0]").value("user@flowdesk.test"))
                .andExpect(jsonPath("$.subject").value("Password Reset"))
                .andExpect(jsonPath("$.text").value("Plain text reset link"))
                .andExpect(jsonPath("$.html").value("<p>HTML reset link</p>"))
                .andExpect(jsonPath("$.reply_to").value("support@flowdesk.test"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\": \"resend-msg-created-001\"}"));

        EmailMessage message = EmailMessage.builder()
                .to("user@flowdesk.test")
                .subject("Password Reset")
                .textBody("Plain text reset link")
                .htmlBody("<p>HTML reset link</p>")
                .replyTo("support@flowdesk.test")
                .build();

        EmailDeliveryResult result = emailSender.send(message);

        mockServer.verify();
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.ACCEPTED);
        assertThat(result.messageId()).isEqualTo("resend-msg-created-001");
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
        // No HTTP request should have been made
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw EmailDeliveryException on HTTP 401 Unauthorized (invalid API key)")
    void shouldHandle401Unauthorized() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\": 401, \"message\": \"API key is invalid\", \"name\": \"unauthorized\"}"));

        EmailMessage message = EmailMessage.of("recipient@flowdesk.test", "Subject", "Body");

        assertThatThrownBy(() -> emailSender.send(message))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("Should throw EmailDeliveryException on HTTP 403 Forbidden (unverified sender domain)")
    void shouldHandle403Forbidden() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\": 403, \"message\": \"Domain not verified\", \"name\": \"forbidden\"}"));

        EmailMessage message = EmailMessage.of("recipient@flowdesk.test", "Subject", "Body");

        assertThatThrownBy(() -> emailSender.send(message))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("403");
    }

    @Test
    @DisplayName("Should throw EmailDeliveryException on HTTP 422 Unprocessable Entity (validation error)")
    void shouldHandle422ValidationError() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\": 422, \"message\": \"Invalid recipient email address\", \"name\": \"validation_error\"}"));

        EmailMessage message = EmailMessage.of("recipient@flowdesk.test", "Subject", "Body");

        assertThatThrownBy(() -> emailSender.send(message))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("422");
    }

    @Test
    @DisplayName("Should throw EmailDeliveryException on HTTP 429 Rate Limit Exceeded")
    void shouldHandle429RateLimit() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\": 429, \"message\": \"Too many requests\", \"name\": \"rate_limit_exceeded\"}"));

        EmailMessage message = EmailMessage.of("recipient@flowdesk.test", "Subject", "Body");

        assertThatThrownBy(() -> emailSender.send(message))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("429");
    }

    @Test
    @DisplayName("Should throw EmailDeliveryException on HTTP 500 Provider Server Error")
    void shouldHandle500ServerError() {
        mockServer.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\": 500, \"message\": \"Internal Server Error\", \"name\": \"internal_server_error\"}"));

        EmailMessage message = EmailMessage.of("recipient@flowdesk.test", "Subject", "Body");

        assertThatThrownBy(() -> emailSender.send(message))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("Should reject null EmailMessage")
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> emailSender.send(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("EmailMessage must not be null");
    }
}

