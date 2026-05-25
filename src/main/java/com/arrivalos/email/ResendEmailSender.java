package com.arrivalos.email;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "arrivalos.email.provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String apiKey;

    public ResendEmailSender(@Value("${arrivalos.email.resend-api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public void send(EmailMessage message) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmailDeliveryException("RESEND_API_KEY is required when arrivalos.email.provider=resend");
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "from", message.from(),
                            "to", new String[] { message.to() },
                            "subject", message.subject(),
                            "html", message.htmlBody(),
                            "text", message.textBody()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            throw new EmailDeliveryException("Resend email delivery failed", exception);
        }
    }
}
