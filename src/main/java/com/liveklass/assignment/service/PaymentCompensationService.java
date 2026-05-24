package com.liveklass.assignment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCompensationService {

    private final PaymentService paymentService;
    private final EnrollmentService enrollmentService;

    @Transactional
    public void compensate(Long paymentId, Long enrollmentId) {
        paymentService.markFailed(paymentId);
        enrollmentService.rollbackToPending(enrollmentId);
    }
}
