package com.liveklass.assignment.api.dto;

public record PaymentResponse(
        Long paymentId,
        String status,
        String enrollmentStatus
) {
    public static PaymentResponse success(Long paymentId) {
        return new PaymentResponse(paymentId, "SUCCESS", "CONFIRMED");
    }
}
