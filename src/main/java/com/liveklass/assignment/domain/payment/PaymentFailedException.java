package com.liveklass.assignment.domain.payment;

import com.liveklass.assignment.common.exception.ErrorCode;

public class PaymentFailedException extends PaymentException {
    public PaymentFailedException(String reason) {
        super(ErrorCode.PAYMENT_GATEWAY_FAILURE,
                "결제 게이트웨이 호출이 실패했습니다. reason=" + reason);
    }
}
