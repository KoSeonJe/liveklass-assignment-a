package com.liveklass.assignment.common.exception;

public class ExternalGatewayException extends BusinessException {
    public ExternalGatewayException(String message) {
        super(ErrorCode.PAYMENT_GATEWAY_FAILURE, message);
    }
}
