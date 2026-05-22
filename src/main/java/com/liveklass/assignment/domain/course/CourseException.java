package com.liveklass.assignment.domain.course;

import com.liveklass.assignment.common.exception.BusinessException;
import com.liveklass.assignment.common.exception.ErrorCode;

public abstract class CourseException extends BusinessException {
    protected CourseException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
