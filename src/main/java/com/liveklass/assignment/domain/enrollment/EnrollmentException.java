package com.liveklass.assignment.domain.enrollment;

import com.liveklass.assignment.common.exception.BusinessException;
import com.liveklass.assignment.common.exception.ErrorCode;

public abstract class EnrollmentException extends BusinessException {
    protected EnrollmentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
