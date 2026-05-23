package com.liveklass.assignment.api.dto;

public record EnrollmentCancelResponse(Long enrollmentId, String status) {

    public static EnrollmentCancelResponse cancelled(Long enrollmentId) {
        return new EnrollmentCancelResponse(enrollmentId, "CANCELLED");
    }
}
