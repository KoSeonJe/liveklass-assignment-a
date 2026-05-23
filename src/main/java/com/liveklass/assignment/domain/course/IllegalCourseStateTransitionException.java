package com.liveklass.assignment.domain.course;

import com.liveklass.assignment.common.exception.ErrorCode;

public class IllegalCourseStateTransitionException extends CourseException {
    public IllegalCourseStateTransitionException(CourseStatus from, CourseStatus to) {
        super(ErrorCode.ILLEGAL_STATE_TRANSITION,
                "허용되지 않은 강의 상태 전이입니다. " + from + " -> " + to);
    }
}
