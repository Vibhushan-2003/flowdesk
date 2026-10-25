package com.flowdesk.email.config;

import com.flowdesk.email.service.EmailSender;
import com.flowdesk.email.service.SmtpEmailSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Bean
    public JavaMailSender javaMailSender(EmailProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.getHost());
        mailSender.setPort(properties.getPort());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        if (properties.isAuth()) {
            mailSender.setUsername(properties.getUsername());
            mailSender.setPassword(properties.getPassword());
        }

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(properties.isAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(properties.isStarttls()));
        props.put("mail.smtp.starttls.required", String.valueOf(properties.isStarttls()));
        if (properties.isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        if (properties.isStarttls() || properties.isSsl()) {
            props.put("mail.smtp.ssl.checkserveridentity", "true");
        }
        props.put("mail.smtp.connectiontimeout", String.valueOf(properties.getConnectionTimeoutMs()));
        props.put("mail.smtp.timeout", String.valueOf(properties.getTimeoutMs()));
        props.put("mail.smtp.writetimeout", String.valueOf(properties.getWriteTimeoutMs()));

        return mailSender;
    }

    @Bean
    public EmailSender emailSender(JavaMailSender mailSender, EmailProperties properties) {
        return new SmtpEmailSender(mailSender, properties);
    }
}
