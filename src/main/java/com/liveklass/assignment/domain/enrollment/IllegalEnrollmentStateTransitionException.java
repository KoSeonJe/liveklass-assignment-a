package com.liveklass.assignment.domain.enrollment;

import com.liveklass.assignment.common.exception.ErrorCode;

public class IllegalEnrollmentStateTransitionException extends EnrollmentException {
    public IllegalEnrollmentStateTransitionException(EnrollmentStatus from, EnrollmentStatus to) {
        super(ErrorCode.ILLEGAL_STATE_TRANSITION,
                "허용되지 않은 수강 신청 상태 전이입니다. " + from + " -> " + to);
    }
}
