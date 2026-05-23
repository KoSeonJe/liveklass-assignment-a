package com.liveklass.assignment.api;

import com.liveklass.assignment.api.dto.PaymentResponse;
import com.liveklass.assignment.common.web.ApiResponse;
import com.liveklass.assignment.facade.PaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enrollments/{enrollmentId}/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @PathVariable Long enrollmentId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        PaymentResponse body = paymentFacade.pay(enrollmentId, userId, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.of(body));
    }
}
