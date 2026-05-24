package com.liveklass.assignment.domain.payment;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final long PROCESSING_DELAY_MS = 2_000L;

    @Override
    public String charge(Long paymentId, int amount) {
        try {
            Thread.sleep(PROCESSING_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("결제 처리 중 인터럽트가 발생했습니다.");
        }
        return "mock-ext-" + UUID.randomUUID();
    }

    @Override
    public void cancel(String externalPaymentKey) {
        try {
            Thread.sleep(PROCESSING_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("결제 취소 중 인터럽트가 발생했습니다.");
        }
        log.info("Mock payment gateway cancel invoked. externalPaymentKey={}", externalPaymentKey);
    }
}
