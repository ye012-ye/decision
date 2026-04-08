package com.ye.decision.service;

import com.ye.decision.domain.dto.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${decision.notification.enabled:true}")
    private boolean enabled;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromAddress;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(NotificationMessage message) {
        if (!enabled) {
            log.debug("Notification disabled, skipping: {}", message.subject());
            return;
        }
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(message.content(), true);
            mailSender.send(mimeMessage);
            log.info("Email sent to {} : {}", message.recipient(), message.subject());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", message.recipient(), e.getMessage(), e);
        }
    }
}
