package com.aedev.myserver.application.dto.sms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendSmsRequest(

        @NotBlank(message = "recipientNumber is required")
        @Pattern(
                regexp = "^(\\+?63|0)9\\d{9}$",
                message = "recipientNumber must be a valid Philippine mobile number (e.g. 09171234567 or +639171234567)"
        )
        String recipientNumber,

        @NotBlank(message = "message is required")
        @Size(max = 918, message = "message must not exceed 918 characters (6 SMS segments)")
        String message
) {
}
