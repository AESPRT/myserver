package com.aedev.myserver.presentation.controller.subscription;

import com.aedev.myserver.application.dto.subscription.ChangePlanRequest;
import com.aedev.myserver.application.dto.subscription.CheckoutResponse;
import com.aedev.myserver.application.service.subscription.ChangePlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
public class ChangedPlanController {

    private final ChangePlanService changePlanService;

    public ChangedPlanController(ChangePlanService changePlanService) {
        this.changePlanService = changePlanService;
    }

    @PostMapping("/change-plan")
    public ResponseEntity<CheckoutResponse> changePlan(@Valid @RequestBody ChangePlanRequest request) {
        CheckoutResponse response = changePlanService.changePlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}