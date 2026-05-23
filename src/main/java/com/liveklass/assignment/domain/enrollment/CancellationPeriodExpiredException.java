package com.liveklass.assignment.domain.enrollment;

import com.liveklass.assignment.common.exception.ErrorCode;

public class CancellationPeriodExpiredException extends EnrollmentException {
    public CancellationPeriodExpiredException(Long enrollmentId) {
        super(ErrorCode.CANCELLATION_PERIOD_EXPIRED,
                "수강 신청 취소 기간이 만료되었습니다. enrollmentId=" + enrollmentId);
    }
}
