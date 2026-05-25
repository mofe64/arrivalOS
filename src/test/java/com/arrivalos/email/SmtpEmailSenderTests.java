package com.arrivalos.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

class SmtpEmailSenderTests {

    @Test
    void sendsPlainTextAndHtmlAsMultipartAlternativeEmail() throws Exception {
        CapturingJavaMailSender mailSender = new CapturingJavaMailSender();
        SmtpEmailSender sender = new SmtpEmailSender(mailSender);

        sender.send(new EmailMessage(
                "client@example.com",
                "ArrivalOS <no-reply@arrivalos.local>",
                "Verify your ArrivalOS email",
                "<p>Verify email</p>",
                "Verify email"));

        assertThat(mailSender.sentMessage).isNotNull();
        assertThat(mailSender.sentMessage.getSubject()).isEqualTo("Verify your ArrivalOS email");
        assertThat(mailSender.sentMessage.getAllRecipients()[0].toString()).isEqualTo("client@example.com");
        assertThat(mailSender.sentMessage.getContent()).isInstanceOf(MimeMultipart.class);

        MimeMultipart multipart = (MimeMultipart) mailSender.sentMessage.getContent();
        assertThat(multipart.getCount()).isGreaterThanOrEqualTo(1);
    }

    private static final class CapturingJavaMailSender implements JavaMailSender {

        private MimeMessage sentMessage;

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), contentStream);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            this.sentMessage = mimeMessage;
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            if (mimeMessages.length > 0) {
                this.sentMessage = mimeMessages[0];
            }
        }

        @Override
        public void send(org.springframework.mail.SimpleMailMessage simpleMessage) {
            throw new UnsupportedOperationException("Simple messages are not used by SmtpEmailSender");
        }

        @Override
        public void send(org.springframework.mail.SimpleMailMessage... simpleMessages) {
            throw new UnsupportedOperationException("Simple messages are not used by SmtpEmailSender");
        }
    }
}
