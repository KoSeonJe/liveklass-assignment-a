package com.liveklass.assignment.service;

import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public void markSuccess(Long paymentId, String externalPaymentKey) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment를 찾을 수 없습니다. paymentId=" + paymentId));
        payment.markSuccess(externalPaymentKey);
    }

    @Transactional
    public void markFailed(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment를 찾을 수 없습니다. paymentId=" + paymentId));
        payment.markFailed();
    }
}
