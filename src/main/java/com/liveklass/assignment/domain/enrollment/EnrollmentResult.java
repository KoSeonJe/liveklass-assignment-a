package com.liveklass.assignment.domain.enrollment;

public sealed interface EnrollmentResult
        permits EnrollmentResult.Pending, EnrollmentResult.Waitlisted {

    record Pending(Long enrollmentId) implements EnrollmentResult {}

    record Waitlisted(int position) implements EnrollmentResult {}

    static EnrollmentResult pending(Long id) {
        return new Pending(id);
    }

    static EnrollmentResult waitlisted(int position) {
        return new Waitlisted(position);
    }

    default boolean isPending() {
        return this instanceof Pending;
    }

    default boolean isWaitlisted() {
        return this instanceof Waitlisted;
    }
}
