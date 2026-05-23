package com.liveklass.assignment.domain.payment;

import com.liveklass.assignment.common.exception.ErrorCode;

public class DuplicatePaymentException extends PaymentException {
    public DuplicatePaymentException(String idempotencyKey) {
        super(ErrorCode.DUPLICATE_PAYMENT,
                "이미 처리된 결제 요청입니다. idempotencyKey=" + idempotencyKey);
    }
}
