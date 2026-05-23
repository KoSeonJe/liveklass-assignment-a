package com.liveklass.assignment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.domain.payment.PaymentStatus;
import com.liveklass.assignment.repository.PaymentRepository;
import com.liveklass.assignment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentServiceTest extends AbstractIntegrationTest {

    @Autowired
    PaymentService paymentService;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    @DisplayName("markSuccess는 Payment를 SUCCESS로 전이하고 externalPaymentKey를 저장한다")
    void mark_success_updates_payment() {
        Long paymentId = savePendingPayment(10L, "key-1", 10000);

        paymentService.markSuccess(paymentId, "ext-key-abc");

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getExternalPaymentKey()).isEqualTo("ext-key-abc");
    }

    @Test
    @DisplayName("markFailed는 Payment를 FAILED로 전이한다")
    void mark_failed_transitions_payment() {
        Long paymentId = savePendingPayment(10L, "key-1", 10000);

        paymentService.markFailed(paymentId);

        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private Long savePendingPayment(Long enrollmentId, String idempotencyKey, int amount) {
        Payment payment = Payment.create(enrollmentId, idempotencyKey, amount);
        return paymentRepository.saveAndFlush(payment).getId();
    }
}
