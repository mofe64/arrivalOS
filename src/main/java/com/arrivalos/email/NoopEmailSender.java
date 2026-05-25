package com.arrivalos.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "arrivalos.email.provider", havingValue = "noop")
public class NoopEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        // Used by automated tests so authentication flows do not depend on an external SMTP service.
    }
}
