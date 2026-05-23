package com.liveklass.assignment.facade;

import com.liveklass.assignment.api.dto.PaymentResponse;
import com.liveklass.assignment.domain.payment.PaymentFailedException;
import com.liveklass.assignment.domain.payment.PaymentGateway;
import com.liveklass.assignment.domain.payment.PaymentGatewayException;
import com.liveklass.assignment.dto.PaymentPreparedInfo;
import com.liveklass.assignment.service.EnrollPaymentService;
import com.liveklass.assignment.service.EnrollmentService;
import com.liveklass.assignment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final EnrollPaymentService enrollPaymentService;
    private final EnrollmentService enrollmentService;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    public PaymentResponse pay(Long enrollmentId, Long userId, String idempotencyKey) {
        PaymentPreparedInfo prepared = enrollPaymentService.confirmAndPreparePayment(enrollmentId, userId, idempotencyKey);

        String externalPaymentKey;
        try {
            externalPaymentKey = paymentGateway.charge(prepared.paymentId(), prepared.amount());
        } catch (PaymentGatewayException e) {
            paymentService.markFailed(prepared.paymentId());
            enrollmentService.rollbackToPending(enrollmentId);
            throw new PaymentFailedException(e.reason());
        }

        try {
            paymentService.markSuccess(prepared.paymentId(), externalPaymentKey);
        } catch (RuntimeException e) {
            bestEffortCancelGateway(externalPaymentKey);
            bestEffortMarkFailed(prepared.paymentId());
            bestEffortRollbackEnrollment(enrollmentId);
            throw e;
        }

        return PaymentResponse.success(prepared.paymentId());
    }

    private void bestEffortCancelGateway(String externalPaymentKey) {
        try {
            paymentGateway.cancel(externalPaymentKey);
        } catch (RuntimeException ignored) {
        }
    }

    private void bestEffortMarkFailed(Long paymentId) {
        try {
            paymentService.markFailed(paymentId);
        } catch (RuntimeException ignored) {
        }
    }

    private void bestEffortRollbackEnrollment(Long enrollmentId) {
        try {
            enrollmentService.rollbackToPending(enrollmentId);
        } catch (RuntimeException ignored) {
        }
    }
}
