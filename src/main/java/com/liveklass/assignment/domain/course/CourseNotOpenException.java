package com.liveklass.assignment.domain.course;

import com.liveklass.assignment.common.exception.ErrorCode;

public class CourseNotOpenException extends CourseException {
    public CourseNotOpenException(Long courseId) {
        super(ErrorCode.CONFLICT, "Course is not OPEN: " + courseId);
    }
}
