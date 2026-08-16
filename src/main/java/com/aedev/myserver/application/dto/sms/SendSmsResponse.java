package com.aedev.myserver.application.dto.sms;

public record SendSmsResponse(
        Long messageId,
        String status,
        String recipient
) {
}
