package com.flowdesk.email.template;

import com.flowdesk.email.model.EmailMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;

/**
 * Service for constructing professionally branded, accessible outbound email messages.
 * <p>
 * Generates paired plain-text and responsive HTML representations adhering to FlowDesk
 * design guidelines. Sanitizes all dynamic values to prevent injection and guarantees
 * readable plain-text content for accessibility and restrictive email clients.
 */
@Service
public class EmailTemplateService {

    /**
     * Builds a password reset notification email.
     *
     * @param recipient Recipient email address.
     * @param resetUrl Validated password reset URL (containing opaque one-time token).
     * @param expiration Time-to-live of the reset link.
     * @return Validated {@link EmailMessage} ready for delivery.
     */
    public EmailMessage buildPasswordResetEmail(String recipient, String resetUrl, Duration expiration) {
        String safeRecipient = HtmlUtils.htmlEscape(recipient != null ? recipient : "");
        String safeUrl = HtmlUtils.htmlEscape(resetUrl != null ? resetUrl : "");
        long minutes = expiration != null ? Math.max(1, expiration.toMinutes()) : 15;

        String subject = "Reset your FlowDesk password";

        String textBody = """
                FlowDesk Password Reset
                ================================================================================

                Hello,

                We received a request to reset the password for your FlowDesk account (%s).

                To choose a new password, open the following link in your browser:
                %s

                Security Notice:
                - This link will expire in %d minutes.
                - This link can only be used once.
                - If you did not request a password reset, you can safely ignore this email.
                  Your account password will not change unless you complete the reset form.

                --------------------------------------------------------------------------------
                FlowDesk Automated Security System
                Do not reply directly to this email.
                ================================================================================
                """.formatted(recipient, resetUrl, minutes);

        String contentHtml = """
                <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Password Reset Request</h2>
                <p style="margin: 0 0 16px 0; color: #334155; line-height: 1.6;">
                    Hello,<br><br>
                    We received a request to reset the password for your FlowDesk account (<strong>%s</strong>).
                </p>
                <div style="margin: 28px 0; text-align: center;">
                    <a href="%s" style="background-color: #2563eb; color: #ffffff; padding: 12px 28px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block; box-shadow: 0 2px 4px rgba(37,99,235,0.2);">Reset Password</a>
                </div>
                <p style="margin: 0 0 12px 0; color: #64748b; font-size: 13px; line-height: 1.5;">
                    If the button above does not work, copy and paste this URL into your browser:<br>
                    <a href="%s" style="color: #2563eb; word-break: break-all;">%s</a>
                </p>
                <div style="margin-top: 24px; padding: 14px; background-color: #f1f5f9; border-left: 4px solid #3b82f6; border-radius: 4px;">
                    <p style="margin: 0; color: #1e293b; font-size: 13px; line-height: 1.5;">
                        <strong>Security Information:</strong> This link expires in <strong>%d minutes</strong> and can only be used once. If you did not request this change, please ignore this email or contact your administrator.
                    </p>
                </div>
                """.formatted(safeRecipient, safeUrl, safeUrl, safeUrl, minutes);

        String htmlBody = wrapHtmlLayout("Reset Your Password", contentHtml);

        return EmailMessage.builder()
                .to(recipient)
                .subject(subject)
                .textBody(textBody)
                .htmlBody(htmlBody)
                .build();
    }

    /**
     * Builds an employee / user invitation notification email.
     *
     * @param recipient Recipient email address.
     * @param inviterName Name or role of the administrator extending the invitation.
     * @param setupUrl Validated account activation URL.
     * @param expiration Time-to-live of the invitation link.
     * @return Validated {@link EmailMessage} ready for delivery.
     */
    public EmailMessage buildUserInvitationEmail(String recipient, String inviterName, String setupUrl, Duration expiration) {
        String safeRecipient = HtmlUtils.htmlEscape(recipient != null ? recipient : "");
        String safeInviter = HtmlUtils.htmlEscape(inviterName != null ? inviterName : "An administrator");
        String safeUrl = HtmlUtils.htmlEscape(setupUrl != null ? setupUrl : "");
        long hours = expiration != null ? Math.max(1, expiration.toHours()) : 48;

        String subject = "You have been invited to join FlowDesk";

        String textBody = """
                Welcome to FlowDesk
                ================================================================================

                Hello,

                %s has invited you to join FlowDesk.

                To set up your account and choose your password, please open the following link:
                %s

                Important:
                - This activation link will expire in %d hours.
                - If you were not expecting this invitation, please contact your IT administrator.

                --------------------------------------------------------------------------------
                FlowDesk Automated Onboarding
                Do not reply directly to this email.
                ================================================================================
                """.formatted(inviterName != null ? inviterName : "An administrator", setupUrl, hours);

        String contentHtml = """
                <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Welcome to FlowDesk</h2>
                <p style="margin: 0 0 16px 0; color: #334155; line-height: 1.6;">
                    Hello,<br><br>
                    <strong>%s</strong> has invited you to activate your FlowDesk account (<strong>%s</strong>).
                </p>
                <div style="margin: 28px 0; text-align: center;">
                    <a href="%s" style="background-color: #2563eb; color: #ffffff; padding: 12px 28px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block; box-shadow: 0 2px 4px rgba(37,99,235,0.2);">Activate Account</a>
                </div>
                <p style="margin: 0 0 12px 0; color: #64748b; font-size: 13px; line-height: 1.5;">
                    If the button above does not work, copy and paste this URL into your browser:<br>
                    <a href="%s" style="color: #2563eb; word-break: break-all;">%s</a>
                </p>
                <div style="margin-top: 24px; padding: 14px; background-color: #f1f5f9; border-left: 4px solid #10b981; border-radius: 4px;">
                    <p style="margin: 0; color: #1e293b; font-size: 13px; line-height: 1.5;">
                        This activation link expires in <strong>%d hours</strong>.
                    </p>
                </div>
                """.formatted(safeInviter, safeRecipient, safeUrl, safeUrl, safeUrl, hours);

        String htmlBody = wrapHtmlLayout("Activate Your FlowDesk Account", contentHtml);

        return EmailMessage.builder()
                .to(recipient)
                .subject(subject)
                .textBody(textBody)
                .htmlBody(htmlBody)
                .build();
    }

    /**
     * Builds a security notice email (e.g. password changed, security alert).
     *
     * @param recipient Recipient email address.
     * @param title Title of the security event.
     * @param details Description of the security event.
     * @return Validated {@link EmailMessage} ready for delivery.
     */
    public EmailMessage buildSecurityNoticeEmail(String recipient, String title, String details) {
        String safeTitle = HtmlUtils.htmlEscape(title != null ? title : "Security Notice");
        String safeDetails = HtmlUtils.htmlEscape(details != null ? details : "");

        String subject = "FlowDesk Security Notice: " + title;

        String textBody = """
                FlowDesk Security Alert
                ================================================================================

                %s

                Details:
                %s

                If you initiated this action, no further steps are required.
                If you did NOT initiate this action, please contact your administrator or security team immediately.

                --------------------------------------------------------------------------------
                FlowDesk Security Team
                Do not reply directly to this email.
                ================================================================================
                """.formatted(title, details);

        String contentHtml = """
                <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">%s</h2>
                <div style="margin: 16px 0; padding: 16px; background-color: #fef2f2; border-left: 4px solid #ef4444; border-radius: 4px;">
                    <p style="margin: 0; color: #991b1b; font-size: 14px; line-height: 1.6;">
                        %s
                    </p>
                </div>
                <p style="margin: 16px 0 0 0; color: #334155; font-size: 13px; line-height: 1.5;">
                    If you initiated this action, no further steps are required.<br>
                    <strong>If you did not initiate this action</strong>, please change your password immediately and contact your FlowDesk administrator.
                </p>
                """.formatted(safeTitle, safeDetails);

        String htmlBody = wrapHtmlLayout("Security Notification", contentHtml);

        return EmailMessage.builder()
                .to(recipient)
                .subject(subject)
                .textBody(textBody)
                .htmlBody(htmlBody)
                .build();
    }

    /**
     * Builds a generic branded notification email.
     */
    public EmailMessage buildNotificationEmail(String recipient, String subject, String headline, String bodyText) {
        String safeHeadline = HtmlUtils.htmlEscape(headline != null ? headline : subject);
        String safeBody = HtmlUtils.htmlEscape(bodyText != null ? bodyText : "");

        String textBody = """
                FlowDesk Notification
                ================================================================================

                %s

                %s

                --------------------------------------------------------------------------------
                FlowDesk System Notification
                Do not reply directly to this email.
                ================================================================================
                """.formatted(headline, bodyText);

        String contentHtml = """
                <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">%s</h2>
                <p style="margin: 0 0 16px 0; color: #334155; line-height: 1.6; white-space: pre-line;">
                    %s
                </p>
                """.formatted(safeHeadline, safeBody);

        String htmlBody = wrapHtmlLayout(subject, contentHtml);

        return EmailMessage.builder()
                .to(recipient)
                .subject(subject)
                .textBody(textBody)
                .htmlBody(htmlBody)
                .build();
    }

    /**
     * Wraps internal email content in the standard, responsive FlowDesk layout container.
     */
    private String wrapHtmlLayout(String title, String innerHtml) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b;">
                    <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%%" style="background-color: #f8fafc; padding: 32px 16px;">
                        <tr>
                            <td align="center">
                                <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%%" style="max-width: 580px; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); overflow: hidden;">
                                    <!-- Header -->
                                    <tr>
                                        <td style="background-color: #0f172a; padding: 24px 32px; text-align: left;">
                                            <span style="color: #ffffff; font-size: 20px; font-weight: 700; letter-spacing: -0.5px;">FlowDesk</span>
                                        </td>
                                    </tr>
                                    <!-- Main Body -->
                                    <tr>
                                        <td style="padding: 32px;">
                                            %s
                                        </td>
                                    </tr>
                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color: #f8fafc; padding: 20px 32px; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b; line-height: 1.5; text-align: center;">
                                            <p style="margin: 0 0 6px 0;">This is an automated notification sent by FlowDesk.</p>
                                            <p style="margin: 0;">Please do not reply directly to this email.</p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(title), innerHtml);
    }
}
