package com.aedev.myserver.presentation.controller.sms;

import com.aedev.myserver.application.dto.sms.SendSmsRequest;
import com.aedev.myserver.application.dto.sms.SendSmsResponse;
import com.aedev.myserver.application.service.sms.SemaphoreService;
import com.aedev.myserver.infrastructure.sms.SemaphoreSendResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standalone SMS-sending endpoint backed by Semaphore. Deliberately
 * generic -- not tied to subscriptions, OTP, or any specific notification
 * type. Callers (mobile app, admin tooling, other backend services)
 * decide what to send and when; this controller's only job is exposing
 * SemaphoreService over HTTP.
 * <p>
 * Not currently wired to any auth/rate-limiting -- an SMS-sending
 * endpoint that's callable by anyone is a real cost/abuse vector
 * (Semaphore bills per message). Add authentication and a per-user or
 * per-IP rate limit before exposing this outside trusted internal
 * callers.
 */
@RestController
@RequestMapping("/api/v1/sms")
public class SmsController {

    private final SemaphoreService semaphoreService;

    public SmsController(SemaphoreService semaphoreService) {
        this.semaphoreService = semaphoreService;
    }

    @PostMapping("/send")
    public ResponseEntity<SendSmsResponse> send(@Valid @RequestBody SendSmsRequest request) {
        SemaphoreSendResult result = semaphoreService.send(request.recipientNumber(), request.message());

        SendSmsResponse response = new SendSmsResponse(
                result.messageId(),
                result.status(),
                result.recipient()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
