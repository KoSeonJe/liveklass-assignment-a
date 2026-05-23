package com.liveklass.assignment.service;

import com.liveklass.assignment.domain.course.CourseStatus;

public record CourseStatusChangeResult(
        Long courseId,
        CourseStatus status,
        int remaining
) {
}
