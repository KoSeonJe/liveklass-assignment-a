package com.liveklass.assignment.facade;

import com.liveklass.assignment.api.dto.PaymentResponse;
import com.liveklass.assignment.domain.payment.PaymentFailedException;
import com.liveklass.assignment.domain.payment.PaymentGateway;
import com.liveklass.assignment.domain.payment.PaymentGatewayException;
import com.liveklass.assignment.dto.PaymentPreparedInfo;
import com.liveklass.assignment.service.EnrollPaymentService;
import com.liveklass.assignment.service.PaymentCompensationService;
import com.liveklass.assignment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final EnrollPaymentService enrollPaymentService;
    private final PaymentService paymentService;
    private final PaymentCompensationService paymentCompensationService;
    private final PaymentGateway paymentGateway;

    public PaymentResponse pay(Long enrollmentId, Long userId, String idempotencyKey) {
        PaymentPreparedInfo prepared = enrollPaymentService.confirmAndPreparePayment(enrollmentId, userId, idempotencyKey);

        String externalPaymentKey;
        try {
            externalPaymentKey = paymentGateway.charge(prepared.paymentId(), prepared.amount());
        } catch (PaymentGatewayException e) {
            compensateOrLog(prepared.paymentId(), enrollmentId, "결제 실패");
            throw new PaymentFailedException(e.reason());
        }

        try {
            paymentService.markSuccess(prepared.paymentId(), externalPaymentKey);
        } catch (RuntimeException e) {
            bestEffortCancelGateway(externalPaymentKey);
            compensateOrLog(prepared.paymentId(), enrollmentId, "결제 성공 후 내부 실패");
            throw e;
        }

        return PaymentResponse.success(prepared.paymentId());
    }

    private void compensateOrLog(Long paymentId, Long enrollmentId, String reason) {
        try {
            paymentCompensationService.compensate(paymentId, enrollmentId);
        } catch (RuntimeException ex) {
            log.error("CRITICAL: 보상 트랜잭션 실패 ({}). enrollmentId={}, paymentId={}",
                    reason, enrollmentId, paymentId, ex);
        }
    }

    private void bestEffortCancelGateway(String externalPaymentKey) {
        try {
            paymentGateway.cancel(externalPaymentKey);
        } catch (RuntimeException ex) {
            log.error("CRITICAL: PG 취소 요청 실패. 수동 환불·대사 필요. externalPaymentKey={}",
                    externalPaymentKey, ex);
        }
    }
}
