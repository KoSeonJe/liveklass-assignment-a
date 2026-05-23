package com.liveklass.assignment.domain.enrollment;

import com.liveklass.assignment.common.exception.ErrorCode;

public class EnrollmentNotFoundException extends EnrollmentException {
    public EnrollmentNotFoundException(Long enrollmentId) {
        super(ErrorCode.NOT_FOUND, "수강 신청을 찾을 수 없습니다. enrollmentId=" + enrollmentId);
    }
}
