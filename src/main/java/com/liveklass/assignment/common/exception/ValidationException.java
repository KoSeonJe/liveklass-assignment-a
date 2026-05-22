package com.liveklass.assignment.common.exception;

public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
}
