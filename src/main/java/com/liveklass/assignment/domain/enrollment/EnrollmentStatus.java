package com.liveklass.assignment.domain.enrollment;

import java.util.Map;
import java.util.Set;

public enum EnrollmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    private static final Map<EnrollmentStatus, Set<EnrollmentStatus>> ALLOWED = Map.of(
            PENDING, Set.of(CONFIRMED),
            CONFIRMED, Set.of(PENDING, CANCELLED),
            CANCELLED, Set.of()
    );

    public void verifyTransitionTo(EnrollmentStatus next) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(next)) {
            throw new IllegalEnrollmentStateTransitionException(this, next);
        }
    }
}
