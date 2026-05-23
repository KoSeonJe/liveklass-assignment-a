package com.liveklass.assignment.domain.payment;

public interface PaymentGateway {
    GatewayResult charge(Long paymentId, int amount);
}
