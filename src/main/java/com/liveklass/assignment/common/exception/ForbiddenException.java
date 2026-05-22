package com.liveklass.assignment.common.exception;

public abstract class ForbiddenException extends BusinessException {
    protected ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
