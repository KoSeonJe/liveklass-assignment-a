package com.liveklass.assignment.api.dto;

import com.liveklass.assignment.domain.course.CourseStatus;
import com.liveklass.assignment.service.CourseStatusChangeResult;

public record CourseStatusChangeResponse(
        Long courseId,
        CourseStatus status,
        int remaining
) {
    public static CourseStatusChangeResponse from(CourseStatusChangeResult result) {
        return new CourseStatusChangeResponse(result.courseId(), result.status(), result.remaining());
    }
}
