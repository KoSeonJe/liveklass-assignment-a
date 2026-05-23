package com.liveklass.assignment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CourseEnrollmentItemResponse(
        Long enrollmentId,
        Long classmateId,
        EnrollmentStatus status,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt
) {
    public static CourseEnrollmentItemResponse from(Enrollment e) {
        return new CourseEnrollmentItemResponse(
                e.getId(),
                e.getClassmateId(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getConfirmedAt(),
                e.getCancelledAt()
        );
    }
}
