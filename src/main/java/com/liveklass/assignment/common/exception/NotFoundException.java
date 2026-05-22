package com.liveklass.assignment.common.exception;

public abstract class NotFoundException extends BusinessException {
    protected NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
