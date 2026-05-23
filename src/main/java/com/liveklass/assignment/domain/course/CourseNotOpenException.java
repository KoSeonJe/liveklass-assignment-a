package com.liveklass.assignment.domain.course;

import com.liveklass.assignment.common.exception.ErrorCode;

public class CourseNotOpenException extends CourseException {
    public CourseNotOpenException(Long courseId) {
        super(ErrorCode.CONFLICT, "강의가 OPEN 상태가 아닙니다. courseId=" + courseId);
    }
}
