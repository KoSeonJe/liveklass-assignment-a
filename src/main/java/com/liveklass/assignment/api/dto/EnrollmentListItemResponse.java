package com.liveklass.assignment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.liveklass.assignment.domain.enrollment.Enrollment;
import com.liveklass.assignment.domain.enrollment.EnrollmentStatus;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollmentListItemResponse(
        Long enrollmentId,
        Long courseId,
        EnrollmentStatus status,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt
) {
    public static EnrollmentListItemResponse from(Enrollment e) {
        return new EnrollmentListItemResponse(
                e.getId(),
                e.getCourseId(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getConfirmedAt(),
                e.getCancelledAt()
        );
    }
}
