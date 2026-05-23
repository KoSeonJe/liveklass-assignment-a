package com.liveklass.assignment.domain.payment;

import com.liveklass.assignment.common.exception.BusinessException;
import com.liveklass.assignment.common.exception.ErrorCode;

public abstract class PaymentException extends BusinessException {
    protected PaymentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
