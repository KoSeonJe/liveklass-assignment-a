package com.liveklass.assignment.domain.course;

import com.liveklass.assignment.common.exception.ErrorCode;

public class IllegalCourseStateTransitionException extends CourseException {
    public IllegalCourseStateTransitionException(CourseStatus from, CourseStatus to) {
        super(ErrorCode.ILLEGAL_STATE_TRANSITION,
                "Illegal course state transition: " + from + " -> " + to);
    }
}
