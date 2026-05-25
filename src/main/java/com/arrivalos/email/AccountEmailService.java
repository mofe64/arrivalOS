package com.arrivalos.email;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.arrivalos.domain.model.AppUser;

@Service
public class AccountEmailService {

    private final EmailSender emailSender;
    private final EmailTemplateRenderer templateRenderer;
    private final String appBaseUrl;
    private final String fromAddress;

    public AccountEmailService(
            EmailSender emailSender,
            EmailTemplateRenderer templateRenderer,
            @Value("${arrivalos.app.base-url}") String appBaseUrl,
            @Value("${arrivalos.email.from}") String fromAddress) {
        this.emailSender = emailSender;
        this.templateRenderer = templateRenderer;
        this.appBaseUrl = stripTrailingSlash(appBaseUrl);
        this.fromAddress = fromAddress;
    }

    public void sendVerificationEmail(AppUser user, String token) {
        String verificationUrl = appBaseUrl + "/verify-email?token=" + token;
        String html = templateRenderer.render("email-templates/email-verification.html", Map.of(
                "fullName", user.getFullName(),
                "verificationUrl", verificationUrl));
        String text = "Verify your ArrivalOS email: " + verificationUrl;
        emailSender.send(new EmailMessage(
                user.getEmail(),
                fromAddress,
                "Verify your ArrivalOS email",
                html,
                text));
    }

    public void sendPasswordResetEmail(AppUser user, String token) {
        String resetUrl = appBaseUrl + "/reset-password?token=" + token;
        String html = templateRenderer.render("email-templates/password-reset.html", Map.of(
                "fullName", user.getFullName(),
                "resetUrl", resetUrl));
        String text = "Reset your ArrivalOS password: " + resetUrl;
        emailSender.send(new EmailMessage(
                user.getEmail(),
                fromAddress,
                "Reset your ArrivalOS password",
                html,
                text));
    }

    public void sendInvitationEmail(String fullName, String email, String token) {
        String invitationUrl = appBaseUrl + "/accept-invite?token=" + token;
        String html = templateRenderer.render("email-templates/account-invitation.html", Map.of(
                "fullName", fullName,
                "invitationUrl", invitationUrl));
        String text = "Accept your ArrivalOS invite: " + invitationUrl;
        emailSender.send(new EmailMessage(
                email,
                fromAddress,
                "Accept your ArrivalOS invite",
                html,
                text));
    }

    private String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
