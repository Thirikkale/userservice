package com.thirikkale.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

/**
 * Email Service for sending verification and notification emails
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    /**
     * Send admin email verification email
     */
    public void sendAdminVerificationEmail(String toEmail, String firstName, String verificationToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verify Your Thirikkale Admin Account");

            String verificationUrl = baseUrl + "/api/v1/auth/admin/verify-email-page?token=" + verificationToken;

            String htmlContent = buildVerificationEmailHtml(firstName, verificationUrl);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    /**
     * Build HTML content for verification email
     */
    private String buildVerificationEmailHtml(String firstName, String verificationUrl) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <style>" +
                "        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }"
                +
                "        .content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }" +
                "        .button { display: inline-block; padding: 15px 30px; background: #667eea; color: white; text-decoration: none; border-radius: 5px; margin: 20px 0; font-weight: bold; }"
                +
                "        .footer { text-align: center; margin-top: 30px; color: #666; font-size: 12px; }" +
                "        .warning { background: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; }"
                +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='container'>" +
                "        <div class='header'>" +
                "            <h1>🚗 Thirikkale Admin</h1>" +
                "            <p>Welcome to the Team!</p>" +
                "        </div>" +
                "        <div class='content'>" +
                "            <h2>Hi " + firstName + ",</h2>" +
                "            <p>Your Thirikkale admin account has been created! To activate your account and start managing the platform, please verify your email address.</p>"
                +
                "            " +
                "            <div style='text-align: center;'>" +
                "                <a href='" + verificationUrl + "' class='button'>Verify Email Address</a>" +
                "            </div>" +
                "            " +
                "            <div class='warning'>" +
                "                <strong>⚠️ Important:</strong>" +
                "                <ul>" +
                "                    <li>This link will expire in 24 hours</li>" +
                "                    <li>After verification, your account status will change from PENDING_ACTIVATION to ACTIVATED</li>"
                +
                "                    <li>You can then log in with your credentials</li>" +
                "                </ul>" +
                "            </div>" +
                "            " +
                "            <p><strong>Alternatively, copy and paste this link:</strong><br>" +
                "            <span style='word-break: break-all; color: #667eea;'>" + verificationUrl + "</span></p>" +
                "            " +
                "            <p style='margin-top: 30px;'>If you didn't request this account, please ignore this email.</p>"
                +
                "        </div>" +
                "        <div class='footer'>" +
                "            <p>© 2025 Thirikkale. All rights reserved.</p>" +
                "            <p>This is an automated message, please do not reply.</p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>";
    }

    /**
     * Send simple text email (fallback)
     */
    public void sendSimpleEmail(String toEmail, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Simple email sent successfully to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
