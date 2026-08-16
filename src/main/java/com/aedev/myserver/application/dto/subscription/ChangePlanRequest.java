package com.aedev.myserver.application.dto.subscription;

import com.aedev.myserver.domain.enums.BillingCycle;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChangePlanRequest(

        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "planId is required")
        String planId,

        @NotNull(message = "cycle is required")
        BillingCycle cycle,

        @NotBlank(message = "cusEmail is required")
        @Email(message = "cusEmail must be a valid email address")
        String cusEmail,

        @NotBlank(message = "cusName is required")
        String cusName,

        String cusPhone
) {
}