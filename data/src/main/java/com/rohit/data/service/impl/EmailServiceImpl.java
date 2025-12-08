package com.rohit.data.service.impl;

import com.rohit.data.entity.Case;
import com.rohit.data.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromEmail;

    @Value("${app.notification.recipient:requester1@example.com}")
    private String toEmail;

    @Override
    public void sendCaseCreatedEmail(Case caseEntity) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("New case created: " + caseEntity.getId() + " - " + caseEntity.getTitle());

            String body = String.format("""
                    A new case has been created.

                    ID: %d
                    Title: %s
                    Country: %s
                    Amount: %.2f
                    Reporter: %s
                    """,
                    caseEntity.getId(),
                    caseEntity.getTitle(),
                    caseEntity.getCountry(),
                    caseEntity.getAmount(),
                    caseEntity.getReporterName());

            message.setText(body);

            mailSender.send(message);
            System.out.println("Email sent successfully for case ID: " + caseEntity.getId());
        } catch (Exception e) {
            System.err
                    .println("Failed to send email for case ID: " + caseEntity.getId() + ". Error: " + e.getMessage());
            // We don't rethrow to avoid rolling back the transaction just because email
            // failed
        }
    }
}
