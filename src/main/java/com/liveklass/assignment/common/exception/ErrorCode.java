package com.liveklass.assignment.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"),
    MISSING_HEADER(HttpStatus.BAD_REQUEST, "MISSING_HEADER"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND"),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT"),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT, "ILLEGAL_STATE_TRANSITION"),
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "CAPACITY_EXCEEDED"),
    CANCELLATION_PERIOD_EXPIRED(HttpStatus.CONFLICT, "CANCELLATION_PERIOD_EXPIRED"),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT, "DUPLICATE_PAYMENT"),
    PAYMENT_GATEWAY_FAILURE(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_FAILURE"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");

    private final HttpStatus status;
    private final String code;

    ErrorCode(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
