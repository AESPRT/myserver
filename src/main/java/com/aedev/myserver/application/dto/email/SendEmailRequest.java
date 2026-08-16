package com.aedev.myserver.application.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendEmailRequest(

        @NotBlank(message = "Recipient email is required")
        @Email(message = "Invalid recipient email")
        String to,

        @NotBlank(message = "Email subject is required")
        String subject,

        @NotBlank(message = "Email content is required")
        String html
) {
}
