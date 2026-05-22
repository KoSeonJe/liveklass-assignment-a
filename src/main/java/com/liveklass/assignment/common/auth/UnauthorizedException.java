package com.liveklass.assignment.common.auth;

import com.liveklass.assignment.common.exception.ForbiddenException;

public class UnauthorizedException extends ForbiddenException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
