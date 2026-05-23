package com.liveklass.assignment.domain.payment;

public interface PaymentGateway {
    String charge(Long paymentId, int amount);

    void cancel(String externalPaymentKey);
}
