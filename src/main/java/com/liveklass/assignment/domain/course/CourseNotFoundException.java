package com.liveklass.assignment.domain.course;

import com.liveklass.assignment.common.exception.ErrorCode;

public class CourseNotFoundException extends CourseException {
    public CourseNotFoundException(Long courseId) {
        super(ErrorCode.NOT_FOUND, "Course not found: " + courseId);
    }
}
