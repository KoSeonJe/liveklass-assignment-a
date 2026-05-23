package com.liveklass.assignment.domain.payment;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {

    private final AtomicReference<GatewayResult> nextResult = new AtomicReference<>(defaultSuccess());

    public void setNextResult(GatewayResult result) {
        if (result == null) {
            throw new IllegalArgumentException("GatewayResult는 null일 수 없습니다.");
        }
        this.nextResult.set(result);
    }

    public void reset() {
        this.nextResult.set(defaultSuccess());
    }

    @Override
    public GatewayResult charge(Long paymentId, int amount) {
        return nextResult.get();
    }

    private static GatewayResult defaultSuccess() {
        return new GatewayResult.Success("mock-ext-" + UUID.randomUUID());
    }
}
