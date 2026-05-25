package com.arrivalos.email;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class EmailTemplateRenderer {

    public String render(String templatePath, Map<String, String> variables) {
        String template = readTemplate(templatePath);
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", escapeHtml(entry.getValue()));
        }
        return rendered;
    }

    private String readTemplate(String templatePath) {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource(templatePath).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read email template " + templatePath, exception);
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
