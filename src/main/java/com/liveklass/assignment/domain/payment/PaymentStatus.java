package com.liveklass.assignment.domain.payment;

import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PENDING, Set.of(SUCCESS, FAILED),
            SUCCESS, Set.of(CANCELLED),
            FAILED, Set.of(),
            CANCELLED, Set.of()
    );

    public void verifyTransitionTo(PaymentStatus next) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(next)) {
            throw new IllegalPaymentStateTransitionException(this, next);
        }
    }
}
