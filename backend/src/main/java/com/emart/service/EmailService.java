package com.emart.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    /**
     * Send OTP email to user
     */
    public void sendOtpEmail(String toEmail, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Your E-Mart Verification Code");

            String htmlContent = buildOtpEmailTemplate(otpCode);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("OTP email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage(), e);
        }
    }

    /**
     * Send simple text email (fallback)
     */
    public void sendSimpleOtpEmail(String toEmail, String otpCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Your E-Mart Verification Code");
            message.setText(buildSimpleOtpMessage(otpCode));

            mailSender.send(message);
            log.info("Simple OTP email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            log.error("Failed to send simple OTP email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send OTP email: " + e.getMessage(), e);
        }
    }

    /**
     * Build HTML email template for OTP
     */
    private String buildOtpEmailTemplate(String otpCode) {
        return "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>E-Mart Verification Code</title>" +
            "</head>" +
            "<body style=\"font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;\">" +
                "<div style=\"background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;\">" +
                    "<h1 style=\"color: white; margin: 0; font-size: 28px;\">E-Mart</h1>" +
                    "<p style=\"color: white; margin: 10px 0 0 0; font-size: 16px;\">Verification Code</p>" +
                "</div>" +
                "<div style=\"background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; border: 1px solid #ddd;\">" +
                    "<h2 style=\"color: #333; margin-top: 0;\">Your Verification Code</h2>" +
                    "<p>Hello! You requested a verification code for your E-Mart account.</p>" +
                    "<div style=\"background: white; border: 2px solid #667eea; border-radius: 8px; padding: 20px; text-align: center; margin: 20px 0;\">" +
                        "<h1 style=\"color: #667eea; font-size: 36px; margin: 0; letter-spacing: 8px; font-family: 'Courier New', monospace;\">" + otpCode + "</h1>" +
                    "</div>" +
                    "<p><strong>Important:</strong></p>" +
                    "<ul style=\"color: #666;\">" +
                        "<li>This code is valid for <strong>5 minutes</strong></li>" +
                        "<li>Do not share this code with anyone</li>" +
                        "<li>If you didn't request this code, please ignore this email</li>" +
                    "</ul>" +
                    "<hr style=\"border: none; border-top: 1px solid #ddd; margin: 30px 0;\">" +
                    "<p style=\"color: #888; font-size: 14px; text-align: center;\">" +
                        "This is an automated message from E-Mart Platform.<br>" +
                        "Please do not reply to this email." +
                    "</p>" +
                "</div>" +
            "</body>" +
            "</html>";
    }

    /**
     * Build simple text message for OTP
     */
    private String buildSimpleOtpMessage(String otpCode) {
        return "E-Mart Verification Code\n\n" +
               "Your verification code is: " + otpCode + "\n\n" +
               "This code is valid for 5 minutes.\n" +
               "Do not share this code with anyone.\n\n" +
               "If you didn't request this code, please ignore this email.\n\n" +
               "---\n" +
               "E-Mart Platform";
    }
}