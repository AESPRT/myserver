package com.aedev.myserver.presentation.controller.email;

import com.aedev.myserver.application.dto.email.SendEmailRequest;
import com.aedev.myserver.application.service.email.EmailService;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/email")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/send")
    public ResponseEntity<EmailResponse> sendEmail(
            @Valid @RequestBody SendEmailRequest request
    ) {
        emailService.sendHtmlEmail(
                request.to(),
                request.subject(),
                request.html()
        );

        return ResponseEntity.ok(
                new EmailResponse(
                        true,
                        "Email sent successfully"
                )
        );
    }

    public record EmailResponse(
            boolean success,
            String message
    ) {
    }
}
