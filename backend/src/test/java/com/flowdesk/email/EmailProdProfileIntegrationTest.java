package com.flowdesk.email;

import com.flowdesk.email.config.EmailConfig;
import com.flowdesk.email.config.EmailProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EmailProdProfileIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EmailConfig.class));

    @Test
    @DisplayName("Under 'prod' profile, valid TLS configuration should successfully load and enforce isProduction()=true")
    void shouldLoadValidProductionConfigUnderProdProfile() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.mail.enabled=true",
                        "app.mail.host=smtp.sendgrid.net",
                        "app.mail.port=587",
                        "app.mail.starttls=true",
                        "app.mail.auth=true",
                        "app.mail.username=apikey",
                        "app.mail.password=SG.dummy-key",
                        "app.mail.from-address=support@flowdesk.io"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EmailProperties properties = context.getBean(EmailProperties.class);
                    assertThat(properties.isProduction()).isTrue();
                    assertThat(properties.getHost()).isEqualTo("smtp.sendgrid.net");
                });
    }

    @Test
    @DisplayName("Under 'prod' profile, startup must fail closed if encryption (STARTTLS or SSL) is missing")
    void shouldFailClosedUnderProdProfileWithoutEncryption() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.mail.enabled=true",
                        "app.mail.host=smtp.sendgrid.net",
                        "app.mail.port=587",
                        "app.mail.starttls=false",
                        "app.mail.ssl=false",
                        "app.mail.auth=true",
                        "app.mail.username=apikey",
                        "app.mail.password=SG.dummy-key",
                        "app.mail.from-address=support@flowdesk.io"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Production email configuration requires STARTTLS or SSL/TLS encryption. Unencrypted SMTP delivery is forbidden in production.");
                });
    }

    @Test
    @DisplayName("Under 'prod' profile, startup must fail closed if host is localhost")
    void shouldFailClosedUnderProdProfileWithLocalhost() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.mail.enabled=true",
                        "app.mail.host=localhost",
                        "app.mail.port=587",
                        "app.mail.starttls=true"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Production email configuration requires a non-localhost SMTP host.");
                });
    }
}
