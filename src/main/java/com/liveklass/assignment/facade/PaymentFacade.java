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

        try {
            String externalPaymentKey = paymentGateway.charge(prepared.paymentId(), prepared.amount());
            paymentService.markSuccess(prepared.paymentId(), externalPaymentKey);
        } catch (PaymentGatewayException e) {
            paymentService.markFailed(prepared.paymentId());
            enrollmentService.rollbackToPending(enrollmentId);
            throw new PaymentFailedException(e.reason());
        } catch (RuntimeException e) {
            //결제 취소 markCancel
            //결제 취소 api 호출
            enrollmentService.rollbackToPending(enrollmentId);
            throw e;
        }

        return PaymentResponse.success(prepared.paymentId());
    }
}
