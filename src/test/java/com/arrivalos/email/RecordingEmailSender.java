package com.arrivalos.email;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> messages = new ArrayList<>();
    private RuntimeException nextFailure;

    @Override
    public void send(EmailMessage message) {
        if (nextFailure != null) {
            RuntimeException failure = nextFailure;
            nextFailure = null;
            throw failure;
        }
        messages.add(message);
    }

    public void clear() {
        messages.clear();
    }

    public List<EmailMessage> messages() {
        return List.copyOf(messages);
    }

    public void failNext(RuntimeException failure) {
        this.nextFailure = failure;
    }

    public Optional<EmailMessage> latestWithSubject(String subject) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            EmailMessage message = messages.get(index);
            if (message.subject().equals(subject)) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }
}
