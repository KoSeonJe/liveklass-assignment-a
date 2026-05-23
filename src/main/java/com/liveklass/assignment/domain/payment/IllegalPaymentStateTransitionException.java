package com.liveklass.assignment.domain.payment;

import com.liveklass.assignment.common.exception.ErrorCode;

public class IllegalPaymentStateTransitionException extends PaymentException {
    public IllegalPaymentStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super(ErrorCode.ILLEGAL_STATE_TRANSITION,
                "허용되지 않은 결제 상태 전이입니다. " + from + " -> " + to);
    }
}
