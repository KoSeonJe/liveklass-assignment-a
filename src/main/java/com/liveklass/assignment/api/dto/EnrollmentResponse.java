package com.liveklass.assignment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollmentResponse(
        String status,
        Long enrollmentId,
        Long courseId,
        Integer position
) {
    public static EnrollmentResponse pending(Long enrollmentId) {
        return new EnrollmentResponse("PENDING", enrollmentId, null, null);
    }

    public static EnrollmentResponse waitlisted(Long courseId, int position) {
        return new EnrollmentResponse("WAITLISTED", null, courseId, position);
    }
}
