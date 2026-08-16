package com.aedev.myserver.application.service.email;

import com.aedev.myserver.application.exception.EmailServiceException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String fromEmail
    ) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    public void sendHtmlEmail(
            String to,
            String subject,
            String html
    ) {
        if (to == null || to.isBlank()) {
            throw new EmailServiceException("Recipient email is required");
        }

        if (subject == null || subject.isBlank()) {
            throw new EmailServiceException("Email subject is required");
        }

        if (html == null || html.isBlank()) {
            throw new EmailServiceException("Email content is required");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException | MailException e) {
            throw new EmailServiceException(
                    "Failed to send email to " + to,
                    e
            );
        }
    }
}
