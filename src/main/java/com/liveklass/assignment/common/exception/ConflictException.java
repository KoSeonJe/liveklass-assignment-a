package com.liveklass.assignment.common.exception;

public abstract class ConflictException extends BusinessException {
    protected ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    protected ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
