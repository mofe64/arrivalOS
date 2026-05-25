package com.arrivalos.email;

public record EmailMessage(
        String to,
        String from,
        String subject,
        String htmlBody,
        String textBody) {
}
