package com.arrivalos.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component
@ConditionalOnProperty(name = "arrivalos.email.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    public SmtpEmailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(message.to());
            helper.setFrom(message.from());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            javaMailSender.send(mimeMessage);
        } catch (MessagingException exception) {
            throw new EmailDeliveryException("Unable to build email message", exception);
        }
    }
}
