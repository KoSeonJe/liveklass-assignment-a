package com.liveklass.assignment.domain.course;

import java.util.Map;
import java.util.Set;

public enum CourseStatus {
    DRAFT,
    OPEN,
    CLOSED;

    private static final Map<CourseStatus, Set<CourseStatus>> ALLOWED = Map.of(
            DRAFT, Set.of(OPEN, CLOSED),
            OPEN, Set.of(CLOSED),
            CLOSED, Set.of(OPEN)
    );

    public void verifyTransitionTo(CourseStatus next) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(next)) {
            throw new IllegalCourseStateTransitionException(this, next);
        }
    }
}
